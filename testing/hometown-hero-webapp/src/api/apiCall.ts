import { ResolvedPromise, resolvePromise } from './resolvePromise';
import axios, { AxiosInstance, AxiosStatic, CancelToken } from 'axios';

import { trackPromise } from 'react-promise-tracker';

//Globally tracking all api calls with react-promise-tracker

export type ApiCallParams = {
    method: 'get' | 'post' | 'put';
    path: string;
    dontTrackRequest?: boolean;
    params?: any;
    config?: any;
    cancelToken?: CancelToken;
    axiosInstance?: AxiosInstance;
};

export default async function apiCall({
    method,
    path,
    dontTrackRequest,
    params,
    cancelToken,
    config,
    axiosInstance,
}: ApiCallParams): Promise<ResolvedPromise> {
    const parameters = method === 'get' ? { params } : { data: params };
    const requestConfig = {
        url: `${path}`,
        method,
        cancelToken: cancelToken,
        ...config,
        ...parameters,
    };
    const axiosHandler: AxiosInstance | AxiosStatic = axiosInstance ?? axios;

    return dontTrackRequest
        ? await resolvePromise(axiosHandler(requestConfig))
        : await resolvePromise(trackPromise(axiosHandler(requestConfig)));
}
