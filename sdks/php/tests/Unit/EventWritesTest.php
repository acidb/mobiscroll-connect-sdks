<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Tests\Unit;

use GuzzleHttp\Client as GuzzleClient;
use GuzzleHttp\Handler\MockHandler;
use GuzzleHttp\HandlerStack;
use GuzzleHttp\Psr7\Response;
use Mobiscroll\Connect\{CalendarEvent, TokenResponse};
use Mobiscroll\Connect\Exceptions\ValidationError;

class EventWritesTest extends BaseTestCase
{
    private const EVENT = [
        'provider' => 'google',
        'id' => 'evt-1',
        'calendarId' => 'primary',
        'title' => 'Team sync',
        'start' => '2026-03-20T09:00:00.000Z',
        'end' => '2026-03-20T09:30:00.000Z',
        'allDay' => false,
        'conference' => 'https://meet.google.com/abc-defg-hij',
        'conferenceData' => ['provider' => 'hangoutsMeet', 'url' => 'https://meet.google.com/abc-defg-hij', 'original' => ['conferenceId' => 'abc-defg-hij']],
    ];

    /**
     * @param array<int, Response> $responses queued in consumption order
     */
    private function mockHttpClient(array $responses): MockHandler
    {
        $mockHandler = new MockHandler($responses);
        $httpClient = new GuzzleClient(['handler' => HandlerStack::create($mockHandler)]);

        $apiClient = (new \ReflectionObject($this->client))->getProperty('apiClient')->getValue($this->client);
        if ($apiClient === null) {
            throw new \RuntimeException('apiClient property is null');
        }
        (new \ReflectionObject($apiClient))->getProperty('httpClient')->setValue($apiClient, $httpClient);
        $apiClient->setCredentials(new TokenResponse(access_token: 'test-token', expires_in: 3600));

        return $mockHandler;
    }

    public function testCreateReturnsTheBareEvent(): void
    {
        $this->mockHttpClient([new Response(201, [], (string)json_encode(self::EVENT))]);

        $event = $this->client->events()->create([
            'provider' => 'google',
            'calendarId' => 'primary',
            'title' => 'Team sync',
            'start' => '2026-03-20T09:00:00Z',
            'end' => '2026-03-20T09:30:00Z',
        ]);

        $this->assertInstanceOf(CalendarEvent::class, $event);
        $this->assertSame('evt-1', $event->id);
        $this->assertSame('https://meet.google.com/abc-defg-hij', $event->conference);
    }

    public function testCreateSendsConferenceFields(): void
    {
        $mockHandler = $this->mockHttpClient([new Response(201, [], (string)json_encode(self::EVENT))]);

        $this->client->events()->create([
            'provider' => 'google',
            'calendarId' => 'primary',
            'title' => 'Team sync',
            'start' => '2026-03-20T09:00:00Z',
            'end' => '2026-03-20T09:30:00Z',
            'attendees' => ['ana@company.com'],
            'conferenceData' => ['autoGenerate' => true],
        ]);

        $request = $mockHandler->getLastRequest();
        $this->assertNotNull($request);
        /** @var array<string, mixed> $sent */
        $sent = json_decode((string)$request->getBody(), true);
        $this->assertSame(['ana@company.com'], $sent['attendees']);
        $this->assertSame(['autoGenerate' => true], $sent['conferenceData']);
    }

    public function testUpdateReturnsTheBareEvent(): void
    {
        $this->mockHttpClient([new Response(200, [], (string)json_encode(['title' => 'Renamed'] + self::EVENT))]);

        $event = $this->client->events()->update([
            'provider' => 'google',
            'calendarId' => 'primary',
            'eventId' => 'evt-1',
            'title' => 'Renamed',
        ]);

        $this->assertSame('Renamed', $event->title);
    }

    public function testCreateAcceptsAnEventEnvelope(): void
    {
        $this->mockHttpClient([new Response(201, [], (string)json_encode(['event' => self::EVENT]))]);

        $event = $this->client->events()->create([
            'provider' => 'google',
            'calendarId' => 'primary',
            'title' => 'Team sync',
            'start' => '2026-03-20T09:00:00Z',
            'end' => '2026-03-20T09:30:00Z',
        ]);

        $this->assertSame('evt-1', $event->id);
    }

    public function testDeleteAcceptsANoContentResponse(): void
    {
        $mockHandler = $this->mockHttpClient([new Response(204)]);

        $result = $this->client->events()->delete(['provider' => 'google', 'calendarId' => 'primary', 'eventId' => 'evt-1']);

        $this->assertSame([], $result);
        $request = $mockHandler->getLastRequest();
        $this->assertNotNull($request);
        $this->assertSame('DELETE', $request->getMethod());
    }

    public function testDeleteFailureThrowsValidationError(): void
    {
        $this->mockHttpClient([new Response(400, [], (string)json_encode(['message' => 'Event not found']))]);

        $this->expectException(ValidationError::class);
        $this->expectExceptionMessage('Event not found');

        $this->client->events()->delete(['provider' => 'google', 'calendarId' => 'primary', 'eventId' => 'evt-1']);
    }
}
