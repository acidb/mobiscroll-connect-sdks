# frozen_string_literal: true

require_relative 'lib/mobiscroll/connect/version'

Gem::Specification.new do |spec|
  spec.name        = 'mobiscroll-connect'
  spec.version     = Mobiscroll::Connect::VERSION
  spec.authors     = ['Mobiscroll']
  spec.email       = ['support@mobiscroll.com']

  spec.summary     = 'Official Ruby SDK for the Mobiscroll Connect API.'
  spec.description = 'Ruby client for the Mobiscroll Connect API. Unified ' \
                     'OAuth, calendars, and events across Google Calendar, ' \
                     'Microsoft Outlook, Apple Calendar, and CalDAV.'
  spec.homepage    = 'https://mobiscroll.com/connect'
  spec.license     = 'MIT'

  spec.required_ruby_version = '>= 3.2'

  spec.metadata = {
    'homepage_uri' => spec.homepage,
    'source_code_uri' => 'https://github.com/acidb/mobiscroll-connect-sdks',
    'bug_tracker_uri' => 'https://github.com/acidb/mobiscroll-connect-sdks/issues',
    'changelog_uri' => 'https://github.com/acidb/mobiscroll-connect-sdks/blob/main/sdks/ruby/CHANGELOG.md',
    'rubygems_mfa_required' => 'true'
  }

  spec.files = Dir[
    'lib/**/*.rb',
    'README.md',
    'LICENSE',
    'mobiscroll-connect.gemspec'
  ]
  spec.require_paths = ['lib']

  spec.add_dependency 'base64', '~> 0.2'
  spec.add_dependency 'faraday', '~> 2.9'

  spec.add_development_dependency 'rspec', '~> 3.13'
  spec.add_development_dependency 'rubocop', '~> 1.65'
  spec.add_development_dependency 'webmock', '~> 3.23'
end
