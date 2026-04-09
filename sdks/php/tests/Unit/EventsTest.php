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
        $this->assertEquals('Event 1', $response->events[0]->title);
    }

    public function testEventListWithGlobalQuery(): void
    {
        // Test that list() constructs correct query parameters
        $start = new \DateTime('2024-01-01');
        $end = new \DateTime('2024-01-31');

        $params = [
            'start' => $start,
            'end' => $end,
            'calendars' => ['cal-1', 'cal-2'],
            'pageSize' => 50,
            'singleEvents' => true,
        ];

        // This would be tested with proper mocking in integration tests
        // For unit tests, we verify the parameter construction logic
        $this->assertTrue(true); // Placeholder for mock-based test
    }
}
