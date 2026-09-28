package ua.kpi.project.compatme.adapter.out.geocoding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ua.kpi.project.compatme.application.exception.ReverseGeocodingException;
import ua.kpi.project.compatme.domain.model.LocationResult;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies {@link NominatimReverseGeocodingAdapter} against a real (local, in-process) HTTP
 * server serving canned Nominatim-shaped responses — avoids needing a mocking library for
 * {@code java.net.http.HttpClient} while still exercising the real HTTP call path.
 */
class NominatimReverseGeocodingAdapterTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void resolveLocation_returnsCountryAndCity_onSuccessfulResponse() throws Exception {
        // GIVEN a server returning a valid Nominatim-shaped JSON body
        String responseBody = """
                {"address": {"city": "Kyiv", "country": "Ukraine"}}
                """;
        server = startServer(responseBody, 200);
        NominatimReverseGeocodingAdapter adapter = adapterFor(server);

        // WHEN
        LocationResult result = adapter.resolveLocation(50.45, 30.52);

        // THEN
        assertThat(result.country()).isEqualTo("Ukraine");
        assertThat(result.city()).isEqualTo("Kyiv");
    }

    @Test
    void resolveLocation_fallsBackToTownWhenCityMissing() throws Exception {
        String responseBody = """
                {"address": {"town": "Smalltown", "country": "Poland"}}
                """;
        server = startServer(responseBody, 200);
        NominatimReverseGeocodingAdapter adapter = adapterFor(server);

        LocationResult result = adapter.resolveLocation(1.0, 1.0);

        assertThat(result.country()).isEqualTo("Poland");
        assertThat(result.city()).isEqualTo("Smalltown");
    }

    @Test
    void resolveLocation_throws_whenNoAddressFound() throws Exception {
        String responseBody = """
                {"error": "Unable to geocode"}
                """;
        server = startServer(responseBody, 200);
        NominatimReverseGeocodingAdapter adapter = adapterFor(server);

        assertThatThrownBy(() -> adapter.resolveLocation(0.0, 0.0))
                .isInstanceOf(ReverseGeocodingException.class);
    }

    @Test
    void resolveLocation_throws_onHttpErrorStatus() throws Exception {
        server = startServer("{}", 500);
        NominatimReverseGeocodingAdapter adapter = adapterFor(server);

        assertThatThrownBy(() -> adapter.resolveLocation(0.0, 0.0))
                .isInstanceOf(ReverseGeocodingException.class);
    }

    private HttpServer startServer(String responseBody, int statusCode) throws Exception {
        HttpServer httpServer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        httpServer.createContext("/reverse", exchange -> {
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        httpServer.start();
        return httpServer;
    }

    private NominatimReverseGeocodingAdapter adapterFor(HttpServer httpServer) {
        NominatimProperties properties = new NominatimProperties();
        properties.setBaseUrl("http://localhost:" + httpServer.getAddress().getPort());
        properties.setMinRequestIntervalMillis(0L); // no need to slow down tests
        return new NominatimReverseGeocodingAdapter(properties, new ObjectMapper());
    }
}
