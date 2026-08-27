<?php

declare(strict_types=1);

namespace Mobiscroll\Connect\Tests\Unit;

use Mobiscroll\Connect\Exceptions\{
    MobiscrollConnectException,
    AuthenticationError,
    CalendarPermissionError,
    ValidationError,
    NotFoundError,
    RateLimitError,
    ServerError,
    NetworkError,
};
use PHPUnit\Framework\TestCase;

class ExceptionsTest extends TestCase
{
    public function testAuthenticationError(): void
    {
        $e = new AuthenticationError('Token expired');

        $this->assertInstanceOf(MobiscrollConnectException::class, $e);
        $this->assertSame('Token expired', $e->getMessage());
        $this->assertSame('AUTHENTICATION_ERROR', $e->getCodeString());
        $this->assertSame(401, $e->getCode());
    }

    public function testCalendarPermissionErrorCarriesAccounts(): void
    {
        $accounts = [['provider' => 'google', 'account' => 'withheld@gmail.com']];
        $e = new CalendarPermissionError('No connected account has calendar access', $accounts);

        // Still an AuthenticationError, so existing catch blocks keep working.
        $this->assertInstanceOf(AuthenticationError::class, $e);
        $this->assertSame('CALENDAR_PERMISSION_REQUIRED', $e->getCodeString());
        $this->assertSame($accounts, $e->getAccounts());
    }

    public function testCalendarPermissionErrorDefaults(): void
    {
        $e = new CalendarPermissionError();

        $this->assertSame('No connected account has calendar access', $e->getMessage());
        $this->assertSame([], $e->getAccounts());
    }

    public function testAuthenticationErrorDefaultMessage(): void
    {
        $e = new AuthenticationError();

        $this->assertSame('Authentication failed', $e->getMessage());
        $this->assertSame('AUTHENTICATION_ERROR', $e->getCodeString());
    }

    public function testValidationErrorWithDetails(): void
    {
        $details = ['email' => ['must be a valid email'], 'title' => ['is required']];
        $e = new ValidationError('Validation failed', $details);

        $this->assertInstanceOf(MobiscrollConnectException::class, $e);
        $this->assertSame('VALIDATION_ERROR', $e->getCodeString());
        $this->assertSame(400, $e->getCode());
        $this->assertSame($details, $e->getDetails());
    }

    public function testValidationErrorDefaultsToEmptyDetails(): void
    {
        $e = new ValidationError();

        $this->assertSame([], $e->getDetails());
        $this->assertSame('Validation failed', $e->getMessage());
    }

    public function testNotFoundError(): void
    {
        $e = new NotFoundError('Calendar not found');

        $this->assertInstanceOf(MobiscrollConnectException::class, $e);
        $this->assertSame('Calendar not found', $e->getMessage());
        $this->assertSame('NOT_FOUND_ERROR', $e->getCodeString());
        $this->assertSame(404, $e->getCode());
    }

    public function testNotFoundErrorDefaultMessage(): void
    {
        $e = new NotFoundError();

        $this->assertSame('Resource not found', $e->getMessage());
    }

    public function testRateLimitErrorWithRetryAfter(): void
    {
        $e = new RateLimitError('Too many requests', 30);

        $this->assertInstanceOf(MobiscrollConnectException::class, $e);
        $this->assertSame('RATE_LIMIT_ERROR', $e->getCodeString());
        $this->assertSame(429, $e->getCode());
        $this->assertSame(30, $e->getRetryAfter());
    }

    public function testRateLimitErrorWithoutRetryAfter(): void
    {
        $e = new RateLimitError();

        $this->assertNull($e->getRetryAfter());
        $this->assertSame('Rate limit exceeded', $e->getMessage());
    }

    public function testServerError(): void
    {
        $e = new ServerError('Gateway timeout', 504);

        $this->assertInstanceOf(MobiscrollConnectException::class, $e);
        $this->assertSame('SERVER_ERROR', $e->getCodeString());
        $this->assertSame(504, $e->getStatusCode());
    }

    public function testServerErrorDefaultStatusCode(): void
    {
        $e = new ServerError();

        $this->assertSame(500, $e->getStatusCode());
        $this->assertSame('Server error', $e->getMessage());
    }

    public function testNetworkError(): void
    {
        $e = new NetworkError('Connection refused');

        $this->assertInstanceOf(MobiscrollConnectException::class, $e);
        $this->assertSame('NETWORK_ERROR', $e->getCodeString());
        $this->assertSame('Connection refused', $e->getMessage());
    }

    public function testBaseExceptionGetCodeString(): void
    {
        $e = new MobiscrollConnectException('Something went wrong', 'CUSTOM_CODE');

        $this->assertSame('CUSTOM_CODE', $e->getCodeString());
    }

    public function testAllErrorsAreThrowable(): void
    {
        $errors = [
            new AuthenticationError(),
            new ValidationError(),
            new NotFoundError(),
            new RateLimitError(),
            new ServerError(),
            new NetworkError(),
        ];

        foreach ($errors as $error) {
            $this->assertInstanceOf(\Throwable::class, $error);
            $this->assertInstanceOf(MobiscrollConnectException::class, $error);
        }
    }
}
