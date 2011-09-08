package foo;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jackson.map.ObjectMapper;

@WebServlet(name = "Test", value = "/test", asyncSupported = true)
public class Test2 extends HttpServlet {

	private Map<String, AsyncContext> ctxs = new ConcurrentHashMap<String, AsyncContext>();

	private BlockingQueue<String> messages = new LinkedBlockingQueue<String>();
	
	private Thread notifier = new Thread(new Runnable() {
		public void run() {
			while (true) {
				try {
					String message = messages.take();
					for (AsyncContext ctx : ctxs.values()) {
						try {
							Writer writer = ctx.getResponse().getWriter();
							writer.write(message);
							writer.flush();
						} catch (Exception e) {
							ctxs.values().remove(ctx);
						}
					}
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	});

	/**
	 * Will open an async connection.
	 */
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {

		res.setCharacterEncoding("utf-8");
		res.setContentType("text/plain");
		res.setHeader("Access-Control-Allow-Origin", "*");
		
		final String id = UUID.randomUUID().toString();

		res.getWriter().print(id);
		res.getWriter().flush();

		final AsyncContext ctx = req.startAsync();
		
		ctx.addListener(new AsyncListener() {

			public void onStartAsync(AsyncEvent event) throws IOException {
			}

			public void onComplete(AsyncEvent event) throws IOException {
				ctxs.remove(id);
			}

			public void onTimeout(AsyncEvent event) throws IOException {
				ctxs.remove(id);
			}

			public void onError(AsyncEvent event) throws IOException {
				ctxs.remove(id);
			}

		});
		
		ctxs.put("foo", ctx);

	}

	/**
	 * User sending a message.
	 */
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		
		req.setCharacterEncoding("utf-8");
		
		System.out.println("ID " + req.getParameter("id") + " MESSAGE " + req.getParameter("message"));
		
		AsyncContext ac = ctxs.get(req.getParameter("id"));
		if (ac == null) return;

		if ("close".equals(req.getParameter("message"))) {
			ac.complete();
			return;
		}

		Map<String, String> data = new LinkedHashMap<String, String>();
		data.put("username", req.getParameter("username"));
		data.put("message", req.getParameter("message"));

		StringWriter message = new StringWriter();
		new ObjectMapper().writeValue(message, data);

		try {
			messages.put(message.getBuffer().toString());
		} catch (InterruptedException e) {
			throw new IOException(e);
		}
	}

	public void init(ServletConfig cfg) throws ServletException {
        super.init(cfg);
        notifier.start();
	}
	
	public void destroy() {
        messages.clear();
        ctxs.clear();
        notifier.interrupt();
	}
	
}
