<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

class TokenResponse
{
    public function __construct(
        public readonly string $access_token,
        public readonly string $token_type = 'Bearer',
        public readonly ?int $expires_in = null,
        public readonly ?string $refresh_token = null,
    ) {
    }

    /**
     * @return array<string, string|int|null>
     */
    public function toArray(): array
    {
        return [
            'access_token' => $this->access_token,
            'token_type' => $this->token_type,
            'expires_in' => $this->expires_in,
            'refresh_token' => $this->refresh_token,
        ];
    }

    /**
     * @param array<string, mixed> $data
     */
    public static function fromArray(array $data): self
    {
        return new self(
            $data['access_token'] ?? throw new \InvalidArgumentException('access_token is required'),
            $data['token_type'] ?? 'Bearer',
            $data['expires_in'] ?? null,
            $data['refresh_token'] ?? null,
        );
    }
}
