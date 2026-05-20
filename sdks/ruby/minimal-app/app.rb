# frozen_string_literal: true

require 'sinatra'
require 'sinatra/json'
require 'json'
require 'dotenv/load'
require 'mobiscroll-connect'

configure do
  enable :sessions
  set :session_secret, ENV.fetch('SESSION_SECRET', SecureRandom.hex(64))
  set :public_folder, File.join(__dir__, 'public')
  set :views, File.join(__dir__, 'views')

  CLIENT_ID     = ENV.fetch('MOBISCROLL_CLIENT_ID') { abort 'MOBISCROLL_CLIENT_ID is required' }
  CLIENT_SECRET = ENV.fetch('MOBISCROLL_CLIENT_SECRET') { abort 'MOBISCROLL_CLIENT_SECRET is required' }
  REDIRECT_URI  = ENV.fetch('MOBISCROLL_REDIRECT_URI') { abort 'MOBISCROLL_REDIRECT_URI is required' }
end

helpers do
  def sdk_client
    client = Mobiscroll::Connect::Client.new(
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      redirect_uri: REDIRECT_URI
    )
    if (tokens = session_tokens)
      client.set_credentials(tokens)
    end
    client.on_tokens_refreshed do |t|
      session[:access_token] = t.access_token
      session[:refresh_token] = t.refresh_token if t.refresh_token
    end
    client
  end

  def session_tokens
    return nil unless session[:access_token]

    Mobiscroll::Connect::TokenResponse.new(
      access_token: session[:access_token],
      token_type: 'Bearer',
      refresh_token: session[:refresh_token]
    )
  end

  def logged_in?
    !session[:access_token].nil?
  end

  def require_login
    redirect '/' unless logged_in?
  end

  def auth_url
    client = Mobiscroll::Connect::Client.new(
      client_id: CLIENT_ID,
      client_secret: CLIENT_SECRET,
      redirect_uri: REDIRECT_URI
    )
    client.auth.generate_auth_url(
      user_id: 'demo-user',
      providers: [
        Mobiscroll::Connect::Provider::GOOGLE,
        Mobiscroll::Connect::Provider::MICROSOFT,
        Mobiscroll::Connect::Provider::APPLE,
        Mobiscroll::Connect::Provider::CALDAV
      ]
    )
  end

  def h(text)
    Rack::Utils.escape_html(text.to_s)
  end
end

# ── Home ──────────────────────────────────────────────────────────────────────

get '/' do
  @auth_url = auth_url
  @logged_in = logged_in?
  erb :index
end

# ── OAuth flow ────────────────────────────────────────────────────────────────

get '/oauth/callback' do
  if (err = params[:error])
    halt 400, erb(:error, locals: { message: "OAuth error: #{h(err)}" })
  end

  code = params[:code]
  halt 400, 'Missing ?code' if code.nil? || code.empty?

  client = Mobiscroll::Connect::Client.new(
    client_id: CLIENT_ID,
    client_secret: CLIENT_SECRET,
    redirect_uri: REDIRECT_URI
  )
  tokens = client.auth.get_token(code)
  session[:access_token] = tokens.access_token
  session[:refresh_token] = tokens.refresh_token
  redirect '/calendars'
rescue Mobiscroll::Connect::Error => e
  halt 400, "Token exchange failed: #{h(e.message)}"
end

get '/logout' do
  session.clear
  redirect '/'
end

# ── Calendars ─────────────────────────────────────────────────────────────────

get '/calendars' do
  require_login
  client = sdk_client
  @calendars = client.calendars.list
  @error = nil
  erb :calendars
rescue Mobiscroll::Connect::Error => e
  @calendars = []
  @error = e.message
  erb :calendars
end

# ── Events ────────────────────────────────────────────────────────────────────

get '/events' do
  require_login
  client = sdk_client

  now = Time.now.utc
  start_dt = params[:start] || now.strftime('%Y-%m-%dT%H:%M')
  end_dt   = params[:end] || (now + 90 * 24 * 3600).strftime('%Y-%m-%dT%H:%M')
  page_size = (params[:page_size] || 100).to_i.clamp(1, 1000)
  single_events = params[:single_events] != 'false'
  next_page_token = params[:next_page_token]

  @result = client.events.list(
    start: start_dt,
    end: end_dt,
    page_size: page_size,
    single_events: single_events,
    next_page_token: next_page_token
  )
  @start_dt = start_dt
  @end_dt = end_dt
  @page_size = page_size
  @single_events = single_events
  @error = nil
  erb :events
rescue Mobiscroll::Connect::Error => e
  @result = nil
  @error = e.message
  erb :events
end

# ── Event edit page ──────────────────────────────────────────────────────────

get '/event-edit' do
  require_login
  client = sdk_client
  @calendars = client.calendars.list rescue []
  @event_id = params[:event_id] || params[:eventId] || ''
  @calendar_id = params[:calendar_id] || params[:calendarId] || ''
  @provider = params[:provider] || ''
  erb :event_edit
end

# ── API: events CRUD (JSON) ──────────────────────────────────────────────────

post '/api/events' do
  content_type :json
  halt 401, json(error: 'Not authenticated') unless logged_in?

  body = JSON.parse(request.body.read)
  client = sdk_client

  event = client.events.create(
    provider: body['provider'],
    calendar_id: body['calendarId'],
    title: body['title'],
    start: body['start'],
    end: body['end'],
    description: body['description'],
    location: body['location'],
    all_day: body['allDay'],
    attendees: parse_attendees(body['attendees']),
    recurrence: parse_recurrence(body['recurrence'])
  )
  json event.to_h
rescue Mobiscroll::Connect::Error => e
  halt 400, json(error: e.message)
end

put '/api/events' do
  content_type :json
  halt 401, json(error: 'Not authenticated') unless logged_in?

  body = JSON.parse(request.body.read)
  client = sdk_client

  opts = {
    description: body['description'],
    location: body['location'],
    all_day: body['allDay'],
    update_mode: body['updateMode'],
    attendees: parse_attendees(body['attendees']),
    recurrence: parse_recurrence(body['recurrence'])
  }.compact

  opts[:start] = body['start'] if body['start']
  opts[:end] = body['end'] if body['end']

  event = client.events.update(
    provider: body['provider'],
    calendar_id: body['calendarId'],
    event_id: body['eventId'],
    title: body['title'],
    **opts
  )
  json event.to_h
rescue Mobiscroll::Connect::Error => e
  halt 400, json(error: e.message)
end

delete '/api/events' do
  content_type :json
  halt 401, json(error: 'Not authenticated') unless logged_in?

  body = JSON.parse(request.body.read)
  client = sdk_client

  client.events.delete(
    provider: body['provider'],
    calendar_id: body['calendarId'],
    event_id: body['eventId'],
    recurring_event_id: body['recurringEventId'],
    delete_mode: body['deleteMode']
  )
  json(success: true)
rescue Mobiscroll::Connect::Error => e
  halt 400, json(error: e.message)
end

private

def parse_attendees(attendees)
  return nil if attendees.nil? || attendees.empty?

  if attendees.is_a?(Array)
    attendees.map do |a|
      a.is_a?(String) ? Mobiscroll::Connect::Attendee.new(email: a) : a
    end
  end
end

def parse_recurrence(rec)
  return nil if rec.nil? || rec['frequency'].nil? || rec['frequency'].empty?

  Mobiscroll::Connect::RecurrenceRule.new(
    frequency: rec['frequency'],
    interval: rec['interval'],
    count: rec['count'],
    by_day: rec['byDay'],
    by_month_day: rec['byMonthDay'],
    by_month: rec['byMonth']
  )
end
