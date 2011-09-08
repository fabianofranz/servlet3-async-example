package foo;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import javax.servlet.AsyncContext;
import javax.servlet.AsyncEvent;
import javax.servlet.AsyncListener;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@WebServlet(urlPatterns="/test2", asyncSupported=true, loadOnStartup=0)
public class Test extends HttpServlet {
	
	private Queue<String> messages = new ConcurrentLinkedQueue<String>();
	private final Executor executor = Executors.newFixedThreadPool(10);
	private List<AsyncContext> ctxs = new ArrayList<AsyncContext>();

	/**
	 * Will open an async connection.
	 */
	protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		
		res.setContentType("text/plain");
        res.setCharacterEncoding("utf-8");
        res.setHeader("Access-Control-Allow-Origin", "*");
        
        PrintWriter writer = res.getWriter();
		writer.print('2');
		writer.print(';');
		writer.print("Hi");
		writer.print(';');
        writer.flush();

		final AsyncContext ctx = req.startAsync();
		
		ctx.addListener(new AsyncListener() {
			
			@Override
			public void onStartAsync(AsyncEvent event) throws IOException { }
			
			@Override
			public void onTimeout(AsyncEvent event) throws IOException { 
				ctxs.remove(ctx);
			}
			
			@Override
			public void onError(AsyncEvent event) throws IOException { 
				ctxs.remove(ctx);
			}
			
			@Override
			public void onComplete(AsyncEvent event) throws IOException { 
				ctxs.remove(ctx);
			}
			
		});
		
		ctxs.add(ctx);
		
	}
	
	protected void doPost(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
		res.setContentType("text/plain");
        res.setCharacterEncoding("utf-8");
		messages.add("MSG " + System.currentTimeMillis());
	}
	
	@Override
	public void init() throws ServletException {
		
		super.init();
		
		// produce random messages
		
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				
				while(true) {
					
					messages.add("MSG " + System.currentTimeMillis());
					
					try {
						Thread.sleep(new Random().nextInt(5) * 1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				
			}
			
		}).start();
		
		// print messages to all users
		
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				
				while(true) {
					
					if (! messages.isEmpty()) {
						
						final String message = messages.poll();
						
						executor.execute(new Runnable() {
							public void run() {
								
								for (AsyncContext ctx : ctxs) {
									
									try {
										
										PrintWriter writer = ctx.getResponse().getWriter(); 
										writer.print(message.length());
										writer.print(';');
										writer.print(message);
										writer.print(';');
										writer.flush();

									} catch (IOException e) {
										e.printStackTrace();
									}
									
								}
								
							};
						});
					}
					
				}
				
			}
			
		}).start();
		
	}
	
}
