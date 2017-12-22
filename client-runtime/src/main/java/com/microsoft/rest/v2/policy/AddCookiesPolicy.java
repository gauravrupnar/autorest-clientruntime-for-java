/**
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License. See License.txt in the project root for
 * license information.
 */

package com.microsoft.rest.v2.policy;

import com.microsoft.rest.v2.http.HttpHeader;
import com.microsoft.rest.v2.http.HttpRequest;
import com.microsoft.rest.v2.http.HttpResponse;
import io.reactivex.Single;
import io.reactivex.functions.Function;

import java.io.IOException;
import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A RequestPolicy which stores cookies based on the response Set-Cookie header and adds cookies to requests.
 */
public final class AddCookiesPolicy implements RequestPolicy {
    private final CookieHandler cookies;
    private final RequestPolicy next;

    private AddCookiesPolicy(CookieHandler cookies, RequestPolicy next) {
        this.cookies = cookies;
        this.next = next;
    }

    @Override
    public Single<HttpResponse> sendAsync(HttpRequest request) {
        try {
            final URI uri = new URI(request.url());

            Map<String, List<String>> cookieHeaders = new HashMap<>();
            for (HttpHeader header : request.headers()) {
                cookieHeaders.put(header.name(), Arrays.asList(request.headers().values(header.name())));
            }

            Map<String, List<String>> requestCookies = cookies.get(uri, cookieHeaders);
            for (Map.Entry<String, List<String>> entry : requestCookies.entrySet()) {
                for (String headerValue : entry.getValue()) {
                    request.headers().set(entry.getKey(), headerValue);
                }
            }

            return next.sendAsync(request).map(new Function<HttpResponse, HttpResponse>() {
                @Override
                public HttpResponse apply(HttpResponse httpResponse) throws Exception {
                    Map<String, List<String>> responseHeaders = new HashMap<>();
                    for (HttpHeader header : httpResponse.headers()) {
                        responseHeaders.put(header.name(), Collections.singletonList(header.value()));
                    }

                    cookies.put(uri, responseHeaders);
                    return httpResponse;
                }
            });
        } catch (URISyntaxException | IOException e) {
            return Single.error(e);
        }
    }

    /**
     * Factory for creating AddCookiesPolicy.
     */
    public static final class Factory implements RequestPolicyFactory {
        private final CookieHandler cookies = new CookieManager();

        @Override
        public RequestPolicy create(RequestPolicy next, RequestPolicyOptions options) {
            return new AddCookiesPolicy(cookies, next);
        }
    }
}