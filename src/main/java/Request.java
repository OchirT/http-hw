import org.apache.http.NameValuePair;

import java.util.ArrayList;
import java.util.List;

public class Request {
    private String method;
    private String path;

    private List<NameValuePair> paramsQuery;
    private List<NameValuePair> paramsPost;

    public Request(String method, String path, List<NameValuePair> paramsQuery, List<NameValuePair> contentType) {
        this.method = method;
        this.path = path;
        this.paramsQuery = paramsQuery;
        this.paramsPost = contentType;

    }

    public String getPath() {
        return path;
    }

    public String getMethod() {
        return method;
    }

    public NameValuePair getQueryParam(String name) {
        return this.paramsQuery.stream().filter(s -> s.getName().equals(name)).findFirst().get();
    }

    public List<NameValuePair> getQueryParams() {
        return this.paramsQuery;
    }

    public NameValuePair getPostParam(String name) {
        return this.paramsPost.stream().filter(s -> s.getName().equals(name)).findFirst().get();
    }

    public List<NameValuePair> getPostParams() {
        return this.paramsPost;
    }

    @Override
    public String toString() {
        return "Request{" +
                "method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", paramsQuery=" + paramsQuery +
                ", contentType=" + paramsPost +
                '}';
    }






}
