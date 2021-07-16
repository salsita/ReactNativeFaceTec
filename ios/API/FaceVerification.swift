//
//  FaceVerification.swift
//  FaceTec
//
//  Created by Alex Serdukov on 24.12.2020.
//  Copyright © 2020 Facebook. All rights reserved.
//

import Foundation
import FaceTecSDK

// TODO Rework as on Android
class FaceVerification {
    // singletone instance
    static let shared = FaceVerification()

    private var serverURL: String?
    private var deviceKey: String?
    private var lastRequest: URLSessionTask?

    private let succeedProperty = "success"
    private let errorMessageProperty = "error"
    private let sessionTokenProperty = "sessionToken"

    private init() {}

    func register(_ serverURL: String, _ deviceKey: String) -> Void {
        self.serverURL = serverURL
        self.deviceKey = deviceKey
    }

    func getSessionToken(sessionTokenCallback: @escaping (String?, Error?) -> Void) -> Void {
        request(nil, "/session-token", "GET", [:]) { response, error in
            if error != nil {
                sessionTokenCallback(nil, error)
                return
            }

            guard let sessionToken = response?[self.sessionTokenProperty] as? String else {
                sessionTokenCallback(nil, FaceVerificationError.emptyResponse)
                return
            }

            sessionTokenCallback(sessionToken, nil)
        }
    }

    func enroll(
        _ sessionId: String,
        _ enrollmentIdentifier: String,
        _ payload: [String : Any],
        enrollmentResultCallback: @escaping ([String: AnyObject]?, Error?) -> Void
    ) -> Void {
        enroll(sessionId, enrollmentIdentifier, payload, withTimeout: nil, withDelegate: nil, callback: enrollmentResultCallback)
    }

    func enroll(
        _ sessionId: String,
        _ enrollmentIdentifier: String,
        _ payload: [String : Any],
        withTimeout: TimeInterval? = nil,
        enrollmentResultCallback: @escaping ([String: AnyObject]?, Error?) -> Void
    ) -> Void {
      enroll(sessionId, enrollmentIdentifier, payload, withTimeout: withTimeout, withDelegate: nil, callback: enrollmentResultCallback)
    }

    func enroll(
        _ sessionId: String,
        _ enrollmentIdentifier: String,
        _ payload: [String : Any],
        withDelegate: URLSessionDelegate? = nil,
        enrollmentResultCallback: @escaping ([String: AnyObject]?, Error?) -> Void
    ) -> Void {
      enroll(sessionId, enrollmentIdentifier, payload, withTimeout: nil, withDelegate: withDelegate, callback: enrollmentResultCallback)
    }

    func enroll(
        _ sessionId: String,
        _ enrollmentIdentifier: String,
        _ payload: [String : Any],
        withTimeout: TimeInterval? = nil,
        withDelegate: URLSessionDelegate? = nil,
        callback enrollmentResultCallback: @escaping ([String: AnyObject]?, Error?) -> Void
    ) -> Void {
        let enrollmentUri = "/enrollment-3d" + enrollmentIdentifier.urlEncoded()

        request(sessionId, enrollmentUri, "POST", payload, withTimeout, withDelegate) { response, error in
            enrollmentResultCallback(response, error)
        }
    }

    func cancelInFlightRequests() {
        if lastRequest != nil {
            lastRequest!.cancel()
            lastRequest = nil
        }
    }

    private func request(
        _ sessionId: String? = nil,
        _ url: String,
        _ method: String,
        _ parameters: [String : Any] = [:],
        _ resultCallback: @escaping ([String: AnyObject]?, Error?) -> Void
    ) -> Void {
        request(sessionId, url, method, parameters, nil, nil, resultCallback)
    }

    private func request(
        _ sessionId: String? = nil,
        _ url: String,
        _ method: String,
        _ parameters: [String : Any] = [:],
        _ withTimeout: TimeInterval? = nil,
        _ resultCallback: @escaping ([String: AnyObject]?, Error?) -> Void
    ) -> Void {
        request(sessionId, url, method, parameters, withTimeout, nil, resultCallback)
    }

    private func request(
        _ sessionId: String? = nil,
        _ url: String,
        _ method: String,
        _ parameters: [String : Any] = [:],
        _ withDelegate: URLSessionDelegate? = nil,
        _ resultCallback: @escaping ([String: AnyObject]?, Error?) -> Void
    ) -> Void {
        request(sessionId, url, method, parameters, nil, withDelegate, resultCallback)
    }

    private func request(
        _ sessionId: String? = nil,
        _ url: String,
        _ method: String,
        _ parameters: [String : Any] = [:],
        _ withTimeout: TimeInterval? = nil,
        _ withDelegate: URLSessionDelegate? = nil,
        _ resultCallback: @escaping ([String: AnyObject]?, Error?) -> Void
    ) {
        let config = URLSessionConfiguration.default
        let request = createRequest(sessionId, url, method, parameters)

        if withTimeout != nil {
            config.timeoutIntervalForRequest = withTimeout!
            config.timeoutIntervalForResource = withTimeout!
        }

        let session = withDelegate == nil ? URLSession(configuration: config)
            : URLSession(configuration: config, delegate: withDelegate, delegateQueue: OperationQueue.main)

        lastRequest = session.dataTask(with: request as URLRequest) { data, response, httpError in
            self.lastRequest = nil

            if httpError != nil {
                resultCallback(nil, httpError)
                return
            }

            guard let json = self.parseResponse(data) else {
                resultCallback(nil, FaceVerificationError.unexpectedResponse)
                return
            }

            if (json[self.succeedProperty] as! Bool == false) {
                let errorMessage = json[self.errorMessageProperty] as? String
                let error: FaceVerificationError = errorMessage == nil ? .unexpectedResponse : .failedResponse(errorMessage!)

                resultCallback(json, error)
                return
            }

            resultCallback(json, nil)
        }

        lastRequest!.resume()
    }

    private func createRequest(
            _ sessionId: String? = nil,
            _ url: String, _ method: String,
            _ parameters: [String : Any] = [:]
        ) -> URLRequest {
            let request = NSMutableURLRequest(url: NSURL(string: serverURL! + url)! as URL)

            request.httpMethod = method.uppercased()

            request.addValue("application/json", forHTTPHeaderField: "Content-Type")
            request.addValue(deviceKey!, forHTTPHeaderField: "X-Device-License-Key")
            request.addValue(FaceTec.sdk.createFaceTecAPIUserAgentString(sessionId != nil ? sessionId! : ""), forHTTPHeaderField: "User-Agent")

            request.httpBody = try! JSONSerialization.data(withJSONObject: parameters, options: JSONSerialization.WritingOptions(rawValue: 0))

            return request as URLRequest
    }

    private func parseResponse(_ data: Data?) -> [String: AnyObject]? {
        guard let data = data else {
            return nil
        }

        guard let json = try? JSONSerialization.jsonObject(
            with: data,
            options: JSONSerialization.ReadingOptions.allowFragments
        ) as? [String: AnyObject] else {
            return nil
        }

        if !(json[succeedProperty] is Bool) {
            return nil
        }

        return json
    }
}
