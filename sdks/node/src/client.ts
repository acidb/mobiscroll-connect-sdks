import axios, { AxiosInstance, AxiosError, AxiosRequestConfig } from 'axios';
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
} from './types';

export class ApiClient {
  private readonly client: AxiosInstance;
  private readonly config: Required<Omit<MobiscrollConnectConfig, 'headers'>> & {
    headers?: Record<string, string>;
  };

  constructor(config: MobiscrollConnectConfig) {
    if (!config.apiKey) {
      throw new MobiscrollConnectError('API key is required');
    }

    this.config = {
      apiKey: config.apiKey,
      baseURL: config.baseURL || 'https://connect.mobiscroll.com/api',
      timeout: config.timeout || 30000,
      headers: config.headers,
    };

    this.client = axios.create({
      baseURL: this.config.baseURL,
      timeout: this.config.timeout,
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.config.apiKey}`,
        ...this.config.headers,
      },
    });

    this.setupInterceptors();
  }

  private setupInterceptors(): void {
    this.client.interceptors.request.use(
      (config) => {
        return config;
      },
      (error) => {
        return Promise.reject(this.handleError(error));
      }
    );

    this.client.interceptors.response.use(
      (response) => {
        return response;
      },
      (error) => {
        return Promise.reject(this.handleError(error));
      }
    );
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

  public setApiKey(apiKey: string): void {
    this.config.apiKey = apiKey;
    this.client.defaults.headers.common['Authorization'] = `Bearer ${apiKey}`;
  }

  public getConfig(): Readonly<typeof this.config> {
    return { ...this.config };
  }

  public get baseURL(): string {
    return this.config.baseURL;
  }
}
