package org.gooddollar.facetec.api;

import okhttp3.Request;
import org.json.JSONObject;

import org.gooddollar.facetec.api.ApiBase;

public final class SessionAPI {
  private SessionAPI() {}

  public interface SessionTokenCallback {
    void onSessionTokenReceived(String sessionToken);
    void onFailure(ApiBase.APIException exception);
  }

  public static void getSessionToken(final SessionTokenCallback callback) {
    // TODO Pass the URLs from the RN during the initializations if we want them custom?
    Request tokenRequest = ApiBase.createRequest(null, "/session-token", "get", null);

    ApiBase.sendRequest(tokenRequest, new ApiBase.APICallback() {
      @Override
      public void onSuccess(JSONObject response) {
        try {
            if(response.has("sessionToken") == false) {
              throw new ApiBase.APIException("FaceTec API response is missing sessionToken", response);
            }
            callback.onSessionTokenReceived(response.getString("sessionToken"));
        } catch (ApiBase.APIException exception) {
          callback.onFailure(exception);
        } catch (Exception exception) {
          callback.onFailure(new ApiBase.APIException(exception, response));
        }
      }

      @Override
      public void onFailure(ApiBase.APIException exception) {
        callback.onFailure(exception);
      }
    });
  }
}
