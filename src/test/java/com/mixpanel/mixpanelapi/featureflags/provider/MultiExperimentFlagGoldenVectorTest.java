package com.mixpanel.mixpanelapi.featureflags.provider;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.mixpanel.mixpanelapi.featureflags.config.LocalFlagsConfig;
import com.mixpanel.mixpanelapi.featureflags.model.SelectedVariant;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Golden-vector tests for multi-experiment flags. The vectors are the cross-SDK contract; the
 * canonical copy lives in the analytics monorepo under
 * go/src/mixpanel.com/flags/api/handlers/testdata/.
 *
 * <p>This copy differs from the canonical file only in its experiment ids. The canonical file uses
 * "e_cta" and "e_search", which {@link SelectedVariant#getExperimentId} cannot represent because it
 * returns a {@link java.util.UUID}. Here they are 00000000-0000-4000-8000-000000000002 and
 * 00000000-0000-4000-8000-000000000003. The variant hash includes the experiment id, and these two
 * ids were chosen so every case keeps the canonical expected variant.
 *
 * <p>Each case evaluates every flag in the case's definitions for the case's context, as
 * {@link LocalFlagsProvider#getAllVariantsByFlag} does, and compares the selected variants with the
 * expected ones. The vectors omit empty experiment fields, so null and false are dropped from the
 * actual variants before comparing.
 */
@RunWith(Parameterized.class)
public class MultiExperimentFlagGoldenVectorTest {
    private static final String VECTORS_RESOURCE = "multi_experiment_flag_test_cases.json";

    private final JSONObject flagsResponse;
    private final Map<String, Object> context;
    private final JSONObject expected;

    public MultiExperimentFlagGoldenVectorTest(String name, JSONObject flagsResponse, Map<String, Object> context, JSONObject expected) {
        this.flagsResponse = flagsResponse;
        this.context = context;
        this.expected = expected;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> cases() {
        JSONArray vectors = new JSONObject(readResource(VECTORS_RESOURCE))
            .getJSONArray("TestMultiExperimentFlagEvaluationScenarios");
        List<Object[]> cases = new ArrayList<>();
        for (int i = 0; i < vectors.length(); i++) {
            JSONObject vector = vectors.getJSONObject(i);
            JSONObject flagsResponse = new JSONObject().put("flags", vector.getJSONArray("flags"));
            cases.add(new Object[] {
                vector.getString("name"),
                flagsResponse,
                vector.getJSONObject("context").toMap(),
                vector.getJSONObject("expected")
            });
        }
        return cases;
    }

    @Test
    public void goldenVector() {
        final MockHttpProvider httpMock = new MockHttpProvider();
        httpMock.setMockResponse("/flags/definitions", flagsResponse.toString());
        LocalFlagsConfig config = LocalFlagsConfig.builder()
            .projectToken("test-token")
            .enablePolling(false)
            .build();
        LocalFlagsProvider provider = new LocalFlagsProvider(config, "1.0.0", null) {
            @Override
            protected String httpGet(String urlString) throws IOException {
                return httpMock.mockHttpGet(urlString);
            }
        };
        provider.startPollingForDefinitions();

        Map<String, SelectedVariant<Object>> variants = provider.getAllVariantsByFlag(context, false);

        JSONObject actual = new JSONObject();
        for (Map.Entry<String, SelectedVariant<Object>> entry : variants.entrySet()) {
            actual.put(entry.getKey(), withoutEmptyAttribution(entry.getValue()));
        }
        assertEquals(expected.toMap(), actual.toMap());
    }

    private static JSONObject withoutEmptyAttribution(SelectedVariant<Object> variant) {
        JSONObject fields = new JSONObject();
        fields.put("variant_key", variant.getVariantKey());
        fields.put("variant_value", variant.getVariantValue());
        if (variant.getExperimentId() != null) {
            fields.put("experiment_id", variant.getExperimentId().toString());
        }
        if (Boolean.TRUE.equals(variant.getIsExperimentActive())) {
            fields.put("is_experiment_active", true);
        }
        return fields;
    }

    private static String readResource(String fileName) {
        InputStream stream = MultiExperimentFlagGoldenVectorTest.class.getClassLoader().getResourceAsStream(fileName);
        assertNotNull(fileName + " is missing from the test resources", stream);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8);
             Scanner scanner = new Scanner(reader).useDelimiter("\\A")) {
            return scanner.hasNext() ? scanner.next() : "";
        } catch (IOException e) {
            throw new IllegalStateException("Could not read " + fileName, e);
        }
    }
}
