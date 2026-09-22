# frozen_string_literal: true

require_relative 'lib/mobiscroll/connect/version'

Gem::Specification.new do |spec|
  spec.name        = 'mobiscroll-connect'
  spec.version     = Mobiscroll::Connect::VERSION
  spec.authors     = ['Mobiscroll']
  spec.email       = ['support@mobiscroll.com']

  spec.summary     = 'Ruby client for Mobiscroll Connect — Google Calendar, ' \
                     'Microsoft Outlook, Apple Calendar and CalDAV through one API.'
  spec.description = 'Ruby client for Mobiscroll Connect, the calendar ' \
                     'connectivity layer for scheduling products. Google ' \
                     'Calendar, Microsoft Outlook, Apple Calendar and CalDAV ' \
                     'through one API, with OAuth 2.0 and webhooks. Backend ' \
                     'only — works with your own UI.'
  spec.homepage    = 'https://mobiscroll.com/connect'
  spec.license     = 'MIT'

  spec.required_ruby_version = '>= 3.2'

  spec.metadata = {
    'homepage_uri' => spec.homepage,
    'source_code_uri' => 'https://github.com/acidb/mobiscroll-connect-sdks',
    'documentation_uri' => 'https://mobiscroll.com/docs/connect/ruby-sdk',
    'bug_tracker_uri' => 'https://github.com/acidb/mobiscroll-connect-sdks/issues',
    'changelog_uri' => 'https://github.com/acidb/mobiscroll-connect-sdks/blob/main/sdks/ruby/CHANGELOG.md',
    'rubygems_mfa_required' => 'true'
  }

  spec.files = Dir[
    'lib/**/*.rb',
    'README.md',
    'CHANGELOG.md',
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
