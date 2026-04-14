<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Tests\Unit;

use Mobiscroll\Connect\{CalendarEvent, EventsListResponse};

class EventsTest extends BaseTestCase
{
    public function testEventFromArray(): void
    {
        $data = [
            'provider' => 'google',
            'id' => 'event-123',
            'calendarId' => 'cal-123',
            'title' => 'Test Event',
            'start' => '2024-01-15T10:00:00Z',
            'end' => '2024-01-15T11:00:00Z',
            'allDay' => false,
            'location' => 'Office',
            'original' => [],
        ];

        $event = CalendarEvent::fromArray($data);

        $this->assertEquals('google', $event->provider);
        $this->assertEquals('event-123', $event->id);
        $this->assertEquals('cal-123', $event->calendarId);
        $this->assertEquals('Test Event', $event->title);
        $this->assertEquals('Office', $event->location);
        $this->assertFalse($event->allDay);
        $this->assertInstanceOf(\DateTime::class, $event->start);
        $this->assertInstanceOf(\DateTime::class, $event->end);
    }

    public function testEventFromArrayWithOptionalFields(): void
    {
        $data = [
            'provider' => 'microsoft',
            'id' => 'event-456',
            'calendarId' => 'cal-456',
            'title' => 'Optional Fields Event',
            'start' => '2024-06-01T09:00:00Z',
            'end' => '2024-06-01T10:00:00Z',
            'allDay' => false,
            'recurringEventId' => 'series-789',
            'color' => '#FF0000',
            'attendees' => [['email' => 'user@example.com']],
            'custom' => ['externalId' => 'ext-123'],
            'conference' => 'https://meet.google.com/abc',
            'availability' => 'busy',
            'privacy' => 'private',
            'status' => 'confirmed',
            'link' => 'https://calendar.google.com/event?eid=123',
            'original' => [],
        ];

        $event = CalendarEvent::fromArray($data);

        $this->assertEquals('series-789', $event->recurringEventId);
        $this->assertEquals('#FF0000', $event->color);
        $this->assertEquals([['email' => 'user@example.com']], $event->attendees);
        $this->assertEquals(['externalId' => 'ext-123'], $event->custom);
        $this->assertEquals('https://meet.google.com/abc', $event->conference);
        $this->assertEquals('busy', $event->availability);
        $this->assertEquals('private', $event->privacy);
        $this->assertEquals('confirmed', $event->status);
        $this->assertEquals('https://calendar.google.com/event?eid=123', $event->link);
    }

    public function testEventMissingRequiredFields(): void
    {
        $this->expectException(\InvalidArgumentException::class);

        CalendarEvent::fromArray([
            'title' => 'Test Event',
        ]);
    }

    public function testEventsListResponseFromArray(): void
    {
        $data = [
            'events' => [
                [
                    'provider' => 'google',
                    'id' => 'event-1',
                    'calendarId' => 'cal-123',
                    'title' => 'Event 1',
                    'start' => '2024-01-15T10:00:00Z',
                    'end' => '2024-01-15T11:00:00Z',
                    'allDay' => false,
                    'original' => [],
                ],
            ],
            'pageSize' => 20,
            'nextPageToken' => 'token-456',
        ];

        $response = EventsListResponse::fromArray($data);

        $this->assertCount(1, $response->events);
        $this->assertEquals(20, $response->pageSize);
        $this->assertEquals('token-456', $response->nextPageToken);
        $this->assertInstanceOf(CalendarEvent::class, $response->events[0]);
        $this->assertEquals('Event 1', $response->events[0]->title);
    }

    public function testEventsListResponseWithNoNextPageToken(): void
    {
        $data = [
            'events' => [],
            'pageSize' => 50,
        ];

        $response = EventsListResponse::fromArray($data);

        $this->assertCount(0, $response->events);
        $this->assertEquals(50, $response->pageSize);
        $this->assertNull($response->nextPageToken);
    }

    public function testEventListParamsBuildCorrectQueryViaReflection(): void
    {
        $start = new \DateTime('2024-01-01T00:00:00Z');
        $end = new \DateTime('2024-01-31T23:59:59Z');

        $params = [
            'start' => $start,
            'end' => $end,
            'calendarIds' => ['google' => ['primary', 'work@company.com']],
            'pageSize' => 50,
            'singleEvents' => true,
            'nextPageToken' => 'abc123',
        ];

        // Access the private buildListQuery method via reflection
        $eventsResource = $this->client->events();
        $ref = new \ReflectionObject($eventsResource);
        $method = $ref->getMethod('buildListQuery');

        $query = $method->invoke($eventsResource, $params);

        $this->assertSame('2024-01-01T00:00:00Z', $query['start']);
        $this->assertSame('2024-01-31T23:59:59Z', $query['end']);
        $this->assertSame('50', $query['pageSize']);
        $this->assertSame('true', $query['singleEvents']);
        $this->assertSame('abc123', $query['nextPageToken']);
        $this->assertArrayHasKey('calendarIds', $query);
        $decoded = json_decode($query['calendarIds'], true);
        $this->assertEquals(['primary', 'work@company.com'], $decoded['google']);
    }

    public function testEventListPageSizeCappedAt1000(): void
    {
        $eventsResource = $this->client->events();
        $ref = new \ReflectionObject($eventsResource);
        $method = $ref->getMethod('buildListQuery');

        $query = $method->invoke($eventsResource, ['pageSize' => 5000]);

        $this->assertSame('1000', $query['pageSize']);
    }

    public function testEventListSingleEventsFalse(): void
    {
        $eventsResource = $this->client->events();
        $ref = new \ReflectionObject($eventsResource);
        $method = $ref->getMethod('buildListQuery');

        $query = $method->invoke($eventsResource, ['singleEvents' => false]);

        $this->assertSame('false', $query['singleEvents']);
    }
}
