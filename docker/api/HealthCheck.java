import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** Health check do container sem depender de curl/wget na imagem JRE: sai com 0 se a URL responder 2xx. */
public class HealthCheck {

	public static void main(String[] args) throws Exception {
		HttpResponse<Void> response = HttpClient.newHttpClient().send(
				HttpRequest.newBuilder(URI.create(args[0])).timeout(Duration.ofSeconds(5)).build(),
				HttpResponse.BodyHandlers.discarding());
		System.exit(response.statusCode() / 100 == 2 ? 0 : 1);
	}
}
