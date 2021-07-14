package org.gooddollar.facetec.processors;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.Promise;
import org.gooddollar.facetec.util.RCTPromise;

import com.facetec.sdk.FaceTecSessionResult;
import com.facetec.sdk.FaceTecSessionStatus;

public class ProcessingSubscriber {
  private Promise promise;

  public ProcessingSubscriber(Promise promise) {
    this.promise = promise;
  }

  public void onProcessingComplete(boolean isSuccess, @Nullable FaceTecSessionResult sessionResult, @Nullable String sessionMessage) {
    if (isSuccess == true) {
      promise.resolve(sessionMessage);
      return;
    }

    if (sessionResult == null) {
      onSessionTokenError("Session result is null.");
      return;
    }

    RCTPromise.rejectWith(promise, sessionResult.getStatus(), sessionMessage);
  }

  public void onSessionTokenError(@Nullable String sessionMessage) {
    String message = "Session could not be started due to an unexpected issue during the network request. Error: " + sessionMessage;

    RCTPromise.rejectWith(promise, FaceTecSessionStatus.UNKNOWN_INTERNAL_ERROR, message);
  }

  public void onSessionContextSwitch() {
    RCTPromise.rejectWith(promise, FaceTecSessionStatus.CONTEXT_SWITCH);
  }

  public void onCameraAccessError() {
    RCTPromise.rejectWith(promise, FaceTecSessionStatus.CAMERA_PERMISSION_DENIED);
  }
}
