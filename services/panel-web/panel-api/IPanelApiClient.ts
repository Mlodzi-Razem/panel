import { User } from "next-auth";

export const BodylessHttpMethods = ["GET", "HEAD", "OPTIONS"] as const;
export const BodyHttpMethods = ["POST", "PUT", "DELETE", "PATCH"] as const;
export const HttpMethods = [...BodylessHttpMethods, ...BodyHttpMethods] as const;

export type HttpMethod = typeof HttpMethods[number];
export type BodylessHttpMethod = typeof BodylessHttpMethods[number];
export type BodyHttpMethod = typeof BodyHttpMethods[number];

export type BodylessRequestInit = Readonly<{
    method: BodylessHttpMethod;
}>;
export type BodyRequestInit = Readonly<{
    method: BodyHttpMethod,
    body?: string | object | Blob
}>;
export type RequestBase = Readonly<{
    url: string;
    headers?: Record<string, string | undefined | null>;
    urlParams?: Record<string, string | undefined | null>;
}>;
export type MethodDependantRequestInit = BodylessRequestInit | BodyRequestInit;

export type PanelApiRequest = Readonly<RequestBase & MethodDependantRequestInit>;
export type PanelApiRequestConfig = Readonly<{
    user: User,
    signal?: AbortSignal;
    cache?: RequestCache
}>;

export default interface IPanelApiClient {
    callApi(req: PanelApiRequest, config?: PanelApiRequestConfig): Promise<Response>;
}
