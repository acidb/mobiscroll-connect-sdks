import { EventEmitter } from 'events';
import axios, {
  AxiosInstance,
  AxiosError,
  AxiosRequestConfig,
  InternalAxiosRequestConfig,
} from 'axios';
import {
  MobiscrollConnectConfig,
  ApiResponse,
  ApiErrorResponse,
  MobiscrollConnectError,
  AuthenticationError,
  NotFoundError,
  ValidationError,
  RateLimitError,
  ServerError,
  NetworkError,
  TokenResponse,
} from './types';

export class ApiClient extends EventEmitter {
  private readonly client: AxiosInstance;
  private readonly config: MobiscrollConnectConfig;
  private credentials?: TokenResponse;
  private isRefreshing = false;
  private refreshSubscribers: ((token: string) => void)[] = [];

  constructor(config: MobiscrollConnectConfig) {
    super();
    if (!config.clientId || !config.clientSecret || !config.redirectUri) {
      throw new MobiscrollConnectError('Client ID, Client Secret and Redirect URI are required');
    }

    this.config = config;

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
    };

    this.client = axios.create({
      baseURL: 'https://connect.mobiscroll.com/api',
      timeout: 30000,
      headers,
    });

    this.setupInterceptors();
  }

  public setCredentials(tokens: TokenResponse): void {
    this.credentials = tokens;
  }

  public getCredentials(): TokenResponse | undefined {
    return this.credentials;
  }

  public getConfig(): MobiscrollConnectConfig {
    return this.config;
  }

  public get baseURL(): string {
    return this.client.defaults.baseURL || '';
  }

  public get axiosInstance(): AxiosInstance {
    return this.client;
  }

  private setupInterceptors(): void {
    this.client.interceptors.request.use(
      (config) => {
        if (this.credentials?.access_token) {
          config.headers.Authorization = `Bearer ${this.credentials.access_token}`;
        }
        return config;
      },
      (error) => {
        return Promise.reject(error);
      }
    );

    this.client.interceptors.response.use(
      (response) => {
        return response;
      },
      async (error: AxiosError<ApiErrorResponse>) => {
        const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

        if (
          error.response?.status === 401 &&
          this.credentials?.refresh_token &&
          originalRequest &&
          !originalRequest._retry
        ) {
          originalRequest._retry = true;

          if (this.isRefreshing) {
            return new Promise((resolve) => {
              this.refreshSubscribers.push((token) => {
                originalRequest.headers.Authorization = `Bearer ${token}`;
                resolve(this.client(originalRequest));
              });
            });
          }

          this.isRefreshing = true;

          try {
            const accessToken = await this.refreshAccessToken();
            this.isRefreshing = false;
            this.onRefreshed(accessToken);

            originalRequest.headers.Authorization = `Bearer ${accessToken}`;
            return this.client(originalRequest);
          } catch {
            this.isRefreshing = false;
            this.refreshSubscribers = [];
            return Promise.reject(this.handleError(error));
          }
        }

        return Promise.reject(this.handleError(error));
      }
    );
  }

  private async refreshAccessToken(): Promise<string> {
    if (!this.credentials?.refresh_token) {
      throw new AuthenticationError('No refresh token available');
    }

    const { clientId, clientSecret, redirectUri } = this.config;
    const credentials = Buffer.from(`${clientId}:${clientSecret}`).toString('base64');

    try {
      const response = await axios.post<TokenResponse>(
        `${this.client.defaults.baseURL}/oauth/token`,
        new URLSearchParams({
          grant_type: 'refresh_token',
          refresh_token: this.credentials.refresh_token,
          redirect_uri: redirectUri,
        }).toString(),
        {
          headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
            Authorization: `Basic ${credentials}`,
            CLIENT_ID: clientId,
          },
        }
      );

      const newTokens = response.data;
      this.credentials = {
        ...this.credentials,
        ...newTokens,
        refresh_token: newTokens.refresh_token || this.credentials.refresh_token,
      };

      this.emit('tokens', this.credentials);

      return this.credentials.access_token;
    } catch {
      throw new AuthenticationError('Failed to refresh token');
    }
  }

  private onRefreshed(token: string): void {
    this.refreshSubscribers.forEach((callback) => callback(token));
    this.refreshSubscribers = [];
  }

  private handleError(error: AxiosError<ApiErrorResponse>): Error {
    if (!error.response) {
      return new NetworkError(error.message);
    }

    const { status, data } = error.response;
    const message = data?.message || error.message;

    switch (status) {
      case 401:
      case 403:
        return new AuthenticationError(message);
      case 404:
        return new NotFoundError(message);
      case 400:
      case 422:
        return new ValidationError(message, data?.details);
      case 429: {
        const retryAfter = error.response.headers['retry-after'];
        return new RateLimitError(message, retryAfter ? Number.parseInt(retryAfter) : undefined);
      }
      case 500:
      case 502:
      case 503:
      case 504:
        return new ServerError(message, status);
      default:
        return new MobiscrollConnectError(message, data?.code);
    }
  }

  public async get<T>(path: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response = await this.client.get<T>(path, config);
    return {
      data: response.data,
      status: response.status,
      headers: response.headers as Record<string, string>,
    };
  }

  public async post<T>(
    path: string,
    data?: unknown,
    config?: AxiosRequestConfig
  ): Promise<ApiResponse<T>> {
    const response = await this.client.post<T>(path, data, config);
    return {
      data: response.data,
      status: response.status,
      headers: response.headers as Record<string, string>,
    };
  }

  public async put<T>(
    path: string,
    data?: unknown,
    config?: AxiosRequestConfig
  ): Promise<ApiResponse<T>> {
    const response = await this.client.put<T>(path, data, config);
    return {
      data: response.data,
      status: response.status,
      headers: response.headers as Record<string, string>,
    };
  }

  public async patch<T>(
    path: string,
    data?: unknown,
    config?: AxiosRequestConfig
  ): Promise<ApiResponse<T>> {
    const response = await this.client.patch<T>(path, data, config);
    return {
      data: response.data,
      status: response.status,
      headers: response.headers as Record<string, string>,
    };
  }

  public async delete<T>(path: string, config?: AxiosRequestConfig): Promise<ApiResponse<T>> {
    const response = await this.client.delete<T>(path, config);
    return {
      data: response.data,
      status: response.status,
      headers: response.headers as Record<string, string>,
    };
  }
}
