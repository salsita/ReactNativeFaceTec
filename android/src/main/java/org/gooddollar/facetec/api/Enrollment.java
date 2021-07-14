package org.gooddollar.facetec.api;

import androidx.annotation.Nullable;
import okhttp3.Request;
import okhttp3.RequestBody;
import org.json.JSONObject;

import org.gooddollar.facetec.api.ApiBase;

public final class Enrollment {
  private Enrollment() {}

  public static void enroll(String sessionId, String enrollmentIdentifier, JSONObject payload, final ApiBase.APICallback callback) {
    enroll(sessionId, enrollmentIdentifier, ApiBase.jsonStringify(payload), null, callback);
  }

  public static void enroll(String sessionId, String enrollmentIdentifier, RequestBody customRequest, final ApiBase.APICallback callback) {
    enroll(sessionId, enrollmentIdentifier, customRequest, null, callback);
  }

  public static void enroll(String sessionId, String enrollmentIdentifier, JSONObject payload, @Nullable Integer timeout, final ApiBase.APICallback callback) {
    enroll(sessionId, enrollmentIdentifier, ApiBase.jsonStringify(payload), timeout, callback);
  }

  public static void enroll(String sessionId, String enrollmentIdentifier, RequestBody customRequest, @Nullable Integer timeout, final ApiBase.APICallback callback) {
    // TODO Fetch API url from env?
    Request enrollmentRequest = ApiBase.createRequest(sessionId, "/enrollment-3d" + enrollmentIdentifier, "post", customRequest);
    ApiBase.sendRequest(enrollmentRequest, timeout, callback);
  }
}
