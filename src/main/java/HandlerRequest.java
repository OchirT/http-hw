

import java.io.BufferedOutputStream;
import java.io.IOException;

public interface HandlerRequest {
    void handle(Request request, BufferedOutputStream responseStream) throws IOException;

}
