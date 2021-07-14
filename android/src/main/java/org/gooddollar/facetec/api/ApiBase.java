package org.gooddollar.facetec.api;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import androidx.annotation.Nullable;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.MediaType;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import org.json.JSONObject;

import com.facetec.sdk.FaceTecSDK;
import org.gooddollar.facetec.api.NetworkingHelpers;

public final class ApiBase {
  private ApiBase() {}
  private final static OkHttpClient http = NetworkingHelpers.getApiClient();
  private static String _serverURL;
  private static String _deviceKey;

  private static String unexpectedMessage = "An unexpected issue during the face verification API call";
  private static String succeedProperty = "success";
  private static String errorMessageProperty = "error";
  private static String sessionTokenProperty = "sessionToken";

  public static class APIException extends IOException {
    JSONObject response = null;

    APIException(String message, @Nullable JSONObject response) {
      super(message);

      this.response = response;
    }

    APIException(Throwable cause, @Nullable JSONObject response) {
      super(cause);

      this.response = response;
    }

    public JSONObject getResponse() {
      return response;
    }
  }

  interface CallbackBase {
    void onFailure(APIException exception);
  }

  public interface APICallback extends CallbackBase {
    void onSuccess(JSONObject response);
  }

  public interface SessionTokenCallback extends CallbackBase {
    void onSessionTokenReceived(String sessionToken);
  }

  public static void init(String serverURL, String deviceKey) {
    _serverURL = serverURL;
    _deviceKey = deviceKey;
  }

  public static RequestBody jsonStringify(JSONObject body) {
    return RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body.toString());
  }

  public static void enroll(String enrollmentIdentifier, JSONObject payload, final APICallback callback) {
    enroll(enrollmentIdentifier, jsonStringify(payload), null, callback);
  }

  public static void enroll(String enrollmentIdentifier, RequestBody customRequest, final APICallback callback) {
    enroll(enrollmentIdentifier, customRequest, null, callback);
  }

  public static void enroll(String enrollmentIdentifier, JSONObject payload, @Nullable Integer timeout, final APICallback callback) {
    enroll(enrollmentIdentifier, jsonStringify(payload), timeout, callback);
  }

  public static void enroll(String enrollmentIdentifier, RequestBody customRequest, @Nullable Integer timeout, final APICallback callback) {
    // TODO Fetch API url from env?
    Request enrollmentRequest = createRequest("/enrollment-3d" + enrollmentIdentifier, "post", customRequest);
    sendRequest(enrollmentRequest, timeout, callback);
  }

  public static Request createRequest(String url, @Nullable String method, @Nullable RequestBody body) {
    Request.Builder request = new Request.Builder()
      .url(_serverURL + url)
      .header("Content-Type", "application/json")
      .header("X-Device-License-Key", _deviceKey)
      .header("User-Agent", FaceTecSDK.createFaceTecAPIUserAgentString(""));

    switch (method) {
      case "post":
        request.post(body);
        break;
      case "put":
        request.put(body);
        break;
      case "get":
        request.get();
        break;
    }

    return request.build();
  }

  public static void sendRequest(Request request, final APICallback requestCallback) {
    sendRequest(request, null, requestCallback);
  }

  public static void sendRequest(Request request, @Nullable Integer timeout, final APICallback requestCallback) {
    OkHttpClient httpClient = http;

    if (timeout != null) {
      httpClient = NetworkingHelpers.setTimeouts(http.newBuilder(), timeout, TimeUnit.MILLISECONDS).build();
    }

    httpClient.newCall(request).enqueue(new Callback() {
      @Override
      public void onResponse(Call call, Response response) throws IOException {
        try {
          String responseString = response.body().string();
          response.body().close();

          JSONObject responseJSON = new JSONObject(responseString);

          if (responseJSON.has(succeedProperty) == false) {
            throw new APIException(unexpectedMessage, responseJSON);
          }

          String errorMessage = null;
          boolean didSucceed = responseJSON.getBoolean(succeedProperty);

          if (didSucceed == true) {
            requestCallback.onSuccess(responseJSON);
            return;
          }

          if (responseJSON.has(errorMessageProperty) == true) {
            errorMessage = responseJSON.getString(errorMessageProperty);
          }

          if (errorMessage == null) {
            errorMessage = unexpectedMessage;
          }

          throw new APIException(errorMessage, responseJSON);
        } catch (APIException exception) {
          requestCallback.onFailure(exception);
        } catch (Exception exception) {
          requestCallback.onFailure(new APIException(exception, null));
        }
      }

      @Override
      public void onFailure(Call call, IOException e) {
        requestCallback.onFailure(new APIException(e, null));
      }
    });
  }
}
