package com.pulse.service;

import java.io.IOException;
import java.net.URI;

interface EndpointHttpClient {
    EndpointHttpResponse get(URI uri) throws IOException;
}
