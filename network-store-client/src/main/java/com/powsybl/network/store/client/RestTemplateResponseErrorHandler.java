/**
 * Copyright (c) 2019, RTE (http://www.rte-france.com)
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.powsybl.network.store.client;

import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResponseErrorHandler;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.powsybl.network.store.model.TopLevelError;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.springframework.http.HttpStatus.Series.CLIENT_ERROR;
import static org.springframework.http.HttpStatus.Series.SERVER_ERROR;

/**
 * @author Geoffroy Jamgotchian <geoffroy.jamgotchian at rte-france.com>
 */
public class RestTemplateResponseErrorHandler implements ResponseErrorHandler {

    @Override
    public boolean hasError(ClientHttpResponse response) throws IOException {
        return response.getStatusCode().series() == CLIENT_ERROR
                || response.getStatusCode().series() == SERVER_ERROR;
    }

    @Override
    public void handleError(ClientHttpResponse response) throws IOException {
        if (response.getStatusCode().series() == HttpStatus.Series.SERVER_ERROR) {
            throw new HttpServerErrorException(response.getStatusCode(), response.getStatusText(),
                    response.getBody().readAllBytes(), StandardCharsets.UTF_8);
        } else if (response.getStatusCode().series() == HttpStatus.Series.CLIENT_ERROR) {
            if (response.getStatusCode() != HttpStatus.NOT_FOUND) {
                throw new HttpClientErrorException(response.getStatusCode(), response.getStatusText(),
                        response.getBody().readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    public static Optional<TopLevelError> parseJsonApiError(String body, ObjectMapper mapper) {
        TopLevelError error = null;
        if (!body.isBlank()) {
            try {
                error = mapper.readValue(body, TopLevelError.class);
            } catch (JsonProcessingException ex) {
                // nothing to do, not json after all
            }
        }
        return Optional.ofNullable(error);
    }
}
