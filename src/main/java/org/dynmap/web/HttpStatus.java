package org.dynmap.web;

public final class HttpStatus {
    private int code;
    private String text;

    public int getCode() {
        return code;
    }

    public String getText() {
        return text;
    }

    public HttpStatus(int code, String text) {
        this.code = code;
        this.text = text;
    }

    // Reference: http://www.w3.org/Protocols/rfc2616/rfc2616-sec10.html
    public static final HttpStatus Continue = new HttpStatus(100, "Continue");
    public static final HttpStatus SwitchingProtocols = new HttpStatus(101, "Switching Protocols");
    public static final HttpStatus OK = new HttpStatus(200, "OK");
    public static final HttpStatus Created = new HttpStatus(201, "Created");
    public static final HttpStatus Accepted = new HttpStatus(202, "Accepted");
    public static final HttpStatus NonAuthoritativeInformation = new HttpStatus(203, "Non-Authoritative Information");
    public static final HttpStatus NoContent = new HttpStatus(204, "No Content");
    public static final HttpStatus ResetContent = new HttpStatus(205, "Reset Content");
    public static final HttpStatus PartialContent = new HttpStatus(206, "Partial Content");
    public static final HttpStatus MultipleChoices = new HttpStatus(300, "Multiple Choices");
    public static final HttpStatus MovedPermanently = new HttpStatus(301, "Moved Permanently");
    public static final HttpStatus Found = new HttpStatus(302, "Found");
    public static final HttpStatus SeeOther = new HttpStatus(303, "See Other");
    public static final HttpStatus NotModified = new HttpStatus(304, "Not Modified");
    public static final HttpStatus UseProxy = new HttpStatus(305, "Use Proxy");
    public static final HttpStatus TemporaryRedirect = new HttpStatus(307, "Temporary Redirect");
    public static final HttpStatus BadRequest = new HttpStatus(400, "Bad Request");
    public static final HttpStatus Unauthorized = new HttpStatus(401, "Unauthorized");
    public static final HttpStatus PaymentRequired = new HttpStatus(402, "Payment Required");
    public static final HttpStatus Forbidden = new HttpStatus(403, "Forbidden");
    public static final HttpStatus NotFound = new HttpStatus(404, "Not Found");
    public static final HttpStatus MethodNotAllowed = new HttpStatus(405, "Method Not Allowed");
    public static final HttpStatus NotAcceptable = new HttpStatus(406, "Not Acceptable");
    public static final HttpStatus ProxyAuthenticationRequired = new HttpStatus(407, "Proxy Authentication Required");
    public static final HttpStatus RequestTimeout = new HttpStatus(408, "Request Timeout");
    public static final HttpStatus Conflict = new HttpStatus(409, "Conflict");
    public static final HttpStatus Gone = new HttpStatus(410, "Gone");
    public static final HttpStatus LengthRequired = new HttpStatus(411, "Length Required");
    public static final HttpStatus PreconditionFailed = new HttpStatus(412, "Precondition Failed");
    public static final HttpStatus RequestEntityTooLarge = new HttpStatus(413, "Request Entity Too Large");
    public static final HttpStatus RequestURITooLong = new HttpStatus(414, "Request-URI Too Long");
    public static final HttpStatus UnsupportedMediaType = new HttpStatus(415, "Unsupported Media Type");
    public static final HttpStatus RequestedRangeNotSatisfiable = new HttpStatus(416, "Requested Range Not Satisfiable");
    public static final HttpStatus ExpectationFailed = new HttpStatus(417, "Expectation Failed");
    public static final HttpStatus InternalServerError = new HttpStatus(500, "Internal Server Error");
    public static final HttpStatus NotImplemented = new HttpStatus(501, "Not Implemented");
    public static final HttpStatus BadGateway = new HttpStatus(502, "Bad Gateway");
    public static final HttpStatus ServiceUnavailable = new HttpStatus(503, "Service Unavailable");
    public static final HttpStatus GatewayTimeout = new HttpStatus(504, "Gateway Timeout");
    public static final HttpStatus HttpVersionNotSupported = new HttpStatus(505, "HTTP Version Not Supported");
}
