<?php

declare(strict_types=1);

namespace Mobiscroll\Connect;

use Psr\SimpleCache\CacheInterface;

class Config
{
    /**
     * @param string|null $webhookPublicKey Pinned `whpk_` key, used only when the webhook keys endpoint cannot be reached
     * @param CacheInterface|null $webhookKeyCache Shares fetched webhook keys across requests and workers
     */
    public function __construct(
        public readonly string $clientId,
        public readonly string $clientSecret,
        public readonly string $redirectUri,
        public readonly ?string $webhookPublicKey = null,
        public readonly ?CacheInterface $webhookKeyCache = null,
    ) {
    }
}
