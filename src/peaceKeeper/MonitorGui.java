package peaceKeeper;

import java.io.ByteArrayInputStream;
import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.ScaleTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import javafx.util.Duration;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;

/**
 * @author Christoffer Wiik
 * @version 1.0
 * @since 2026-03-02
 * 
 * GUI to monitor the sound levels and corresponding output.
 * Sends XML request to server over TCP when violation of the threshold is done.
 * Receives XML response from server to either show text, image or play a sound.
 * */
public class MonitorGui extends Application {

	private Client client;
	private Label rmsLabel;
	private Slider thresholdSlider;
	private Canvas canvas;
	private GraphicsContext context;
	private Stage primaryStage;
	private volatile boolean running = false;
	private Thread listenerThread;
	private SoundMonitor monitor;
	private Label connected;
	private Label thresholdLabel;
	private ChangeListener<Number> threshold;
	
	/**
	 * Creates and sets style to the UI's components.
	 * Sets listeners and starts up the server connection and sound monitoring.
	 * @param stage the primary stage for the UI.
	 * */
	@Override
	public void start(Stage stage) {
		
		this.primaryStage = stage;
		
		thresholdLabel = new Label("RMS over threshold: 0");
		thresholdLabel.setStyle(
				"-fx-font-size: 22px;" +
				"-fx-text-fill: #00eaff;" +     
				"-fx-font-weight: bold;" +
				"-fx-effect: dropshadow(gaussian, #00eaff, 15, 0.5, 0, 0);" +
				"-fx-font-family: 'Segoe UI';");
		
		rmsLabel = new Label("RMS: 0");
		rmsLabel.setStyle(
				"-fx-font-size: 22px;" +
				"-fx-text-fill: #00eaff;" +     
				"-fx-font-weight: bold;" +
				"-fx-effect: dropshadow(gaussian, #00eaff, 15, 0.5, 0, 0);" +
				"-fx-font-family: 'Segoe UI';");
		
		thresholdSlider = new Slider(1000, 3000, 2000);
		thresholdSlider.setShowTickLabels(true);
		thresholdSlider.setShowTickMarks(true);
		thresholdSlider.setStyle(
			    " -fx-control-inner-background: linear-gradient(to right, #0a0f1f, #1a1f3c);" +
			    " -fx-tick-label-fill: #ff00ff;" +                                             
			    " -fx-font-size: 14px;" +
			    " -fx-font-weight: bold;" +
			    " -fx-focus-color: #00eaff;" +                                                 
			    " -fx-faint-focus-color: transparent;" +
			    " -fx-base: #5500ff;" +                                                        
			    " -fx-background-insets: 0;" +
			    " -fx-background-radius: 5;" +
			    " -fx-padding: 12;"
			);
		
		connected = new Label("<== Peace Keeper ==>");
		connected.setStyle(
				"-fx-font-size: 22px;" +
				"-fx-text-fill: #00eaff;" +     
				"-fx-font-weight: bold;" +
				"-fx-effect: dropshadow(gaussian, #00eaff, 15, 0.5, 0, 0);" +
				"-fx-font-family: 'Segoe UI';");
		
		Button connect = new Button("Connect");
		connect.setStyle(
				"-fx-background-color: linear-gradient(to bottom right, #5500ff, #2a0066);" +
				"-fx-text-fill: #00eaff;" +
				"-fx-font-size: 20px;" + 
				"-fx-font-weight: bold;" +
				"-fx-background-radius: 8;" +
				"-fx-effect: dropshadow(gaussian, #ff00ff, 20, 0.5, 0, 0);");
		
		Button disconnect = new Button("Disconnect");
		disconnect.setStyle(
				"-fx-background-color: linear-gradient(to bottom right, #5500ff, #2a0066);" +
				"-fx-text-fill: #00eaff;" +
				"-fx-font-size: 20px;" + 
				"-fx-font-weight: bold;" +
				"-fx-background-radius: 8;" +
				"-fx-effect: dropshadow(gaussian, #ff00ff, 20, 0.5, 0, 0);");
		
		Button exit = new Button("Exit");
		exit.setStyle(
				"-fx-background-color: linear-gradient(to bottom right, #5500ff, #2a0066);" +
				"-fx-text-fill: #00eaff;" +
				"-fx-font-size: 18px;" + 
				"-fx-font-weight: bold;" +
				"-fx-background-radius: 8;" +
				"-fx-effect: dropshadow(gaussian, #ff00ff, 20, 0.5, 0, 0);");
		
		connect.setOnAction(e -> {
			if(socketInit()) {
				startMonitor();
				responseListener();
			}

			if(client != null) {
				connected.setText("Connected");
			}
		});
		
		disconnect.setOnAction(e -> {
			stopListener();
			
			running = false;
			if(monitor != null) {
				monitor.stop();
				monitor = null;
			}
			
			if(client != null) {
				try {
					client.close();
					client = null;
				}
				catch(Exception ex) {
					ex.printStackTrace();
				}
			}
			connected.setText("Disconnected");
			rmsLabel.setText("RMS: 0.0");
			thresholdLabel.setText("RMS over threshold: 0");
		});
		
		exit.setOnAction(e -> {
			primaryStage.fireEvent(new WindowEvent(primaryStage, WindowEvent.WINDOW_CLOSE_REQUEST));
		});
		
		primaryStage.setOnCloseRequest(event -> {
			stopListener();
			try {
				if(monitor != null) {
					monitor.stop();
					monitor = null;
				}
				if(client != null) {
					client.close();
					client = null;
				}
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		});
		
		canvas = new Canvas(600, 50);
		context = canvas.getGraphicsContext2D();
		
		StackPane root = new StackPane();
		
		HBox topLayout = new HBox();
		
		topLayout.setSpacing(50);
		topLayout.getChildren().addAll(connect, disconnect, connected);
		topLayout.setPadding(new Insets(30));
		
		VBox layout = new VBox();
		layout.setSpacing(20);
		layout.getChildren().addAll(rmsLabel, thresholdLabel, thresholdSlider, canvas, exit);
		
		layout.setPadding(new Insets(20));
		
		VBox box = new VBox();
		box.getChildren().addAll(topLayout, layout);
		
		root.getChildren().add(box);
		stage.setScene(new Scene(root, 700, 500));
		stage.setTitle("Peace Keeper");
		stage.getIcons().add(new Image(getClass().getResourceAsStream("resources/images/soundwave.png")));
		
		root.setStyle(
				"-fx-background-color: linear-gradient(to bottom right, #0d0221, #1a0535, #320a63);" +
				"-fx-border-color: linear-gradient(to right, #00eaff, #ff00ff);" +
				"-fx-border-width: 4px;" +
				"-fx-border-radius: 10;" +
				"-fx-background-radius: 10;" +
				"-fx-effect: dropshadow(gaussian, #ff00ff, 40, 0.8, 0, 0);");
		
		stage.show();
	}
	
	/**
	 * Creates a client and connects to server.
	 * @return true if connected false otherwise.
	 * 
	 * @throws Exception on error
	 * */
	private boolean socketInit() {
		try {
			client = new Client("localhost", 3000);
			//client = new Client("192.168.1.104", 3000);
			return true;
		}
		catch(Exception e) {
			e.printStackTrace();
			client = null;
			return false;
		}
	}
	
	/**
	 * Draws "sound bars" to imitate sound level.
	 * @param lvl the sound level as RMS.
	 * */
	private void drawLvl(float lvl) {
		
		int barCount = 60;
		float[] bars = generateBar(lvl, barCount);
		
		double maxWidth = canvas.getWidth();
		double maxHeight = canvas.getHeight();
		double barWidth = maxWidth / barCount;
		
		context.clearRect(0,  0,  maxWidth, maxHeight);
		
		Stop[] stop = new Stop[] {
				new Stop(0.0, Color.web("#39ff14")),
				new Stop(0.5, Color.web("#8a2be2")),
				new Stop(1.0, Color.web("#ff00ff"))
		};
		
		LinearGradient grad = new LinearGradient(
				0, 1, 0, 0, true, CycleMethod.NO_CYCLE, stop);
		
		context.setFill(grad);
		
		for(int i = 0; i < barCount; i++) {
			double level = Math.min(bars[i] / 200.0, 1.0);
			
			double barHeight = maxHeight * level;
			
			double x = i * barWidth;
			double y = maxHeight - barHeight;
			
			context.fillRect(x,  y,  barWidth - 4,  barHeight);
		}
	}
	
	/**
	 * Creates bars to be used in the "sound bar".
	 * @param lvl the sound level as RMS.
	 * @param count the number of bars to create.
	 * @return bars a number of rectangles.
	 * */
	private float[] generateBar(float lvl, int count) {
		float[] bars = new float[count];
		
		for(int i = 0; i < count; i++) {
			float variation = (float)(Math.random() * 0.4 + 0.8);
			bars[i] = lvl * variation;
		}
		
		return bars;
	}

	/**
	 * Starts monitoring the sound level.
	 * Updates the RMS label and send request to server when violation of threshold.
	 * */
	private void startMonitor() {
		if(monitor != null) {
			monitor.stop();
			monitor = null;
		}
		if(threshold != null) {
			thresholdSlider.valueProperty().removeListener(threshold);
		}
		
		monitor = new SoundMonitor((float) thresholdSlider.getValue());
		monitor.setListener(new SoundListener() {
			@Override
			public void onRmsUpdate(float rms) {
				Platform.runLater(() -> {
					rmsLabel.setText("Rms: " + rms);
					drawLvl(rms);
				});
			}
			
			public void onThreshold(float rms, float exceed) {
				Platform.runLater(() -> {
					rmsLabel.setText("RMS: " + rms);
					thresholdLabel.setText("RMS over threshold: " + exceed);
					drawLvl(rms);
					if(client != null) {
						String violation = buildReq(rms, exceed);
						client.send(violation);
					}
				});
			}
		});
		
		threshold = (obs, oldVal, newVal) -> {
			monitor.setThreshold(newVal.floatValue());
		};
		thresholdSlider.valueProperty().addListener(threshold);
		
		new Thread(monitor).start();
	}
	
	/**
	 * Build a XML request to the server to be sent.
	 * 
	 * @param rms the sound level value.
	 * @param exceed the calculated value that are exceeding threshold.
	 * @return violation a XML string.
	 * */
	private String buildReq(float rms, float exceed) {
		String violation = 
				"<alert>" + 
						"<type>thresholdExceeded</type>" +
						"<rms>" + rms + "</rms>" + 
						"<violationNumber>" + exceed + "</violationNumber>" +
				"</alert>\n";
		
		return violation;
	}
	
	/**
	 * sets up a Listener for server response
	 * on a own thread.
	 * */
	private void responseListener() {
		running = true;
		
		listenerThread = new Thread(() -> {
			try {
				while(running) {
					String response = client.receive();
					if(response == null) {
						break;
					}
					serverResponse(response);
				}
			}
			catch(Exception e) {
				if(running) {
					e.printStackTrace();
				}	
			}
		});
		
		listenerThread.setDaemon(true);
		listenerThread.start();
	}
	
	/**
	 * sets running to false to stop the listener from listening.
	 * */
	private void stopListener() {
		running = false;
		
		try{
			if(client != null) {
				client.close();
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		try { Thread.sleep(50); } catch(Exception ignored) {}

		listenerThread = null;
	}
	
	/**
	 * Receives the server response and deconstructs the response for the correct response.
	 * 
	 * @param msg message string from server
	 * @throw error if message is malformed.
	 * */
	private void serverResponse(String msg) {
		if(msg == null || msg.trim().isEmpty()) {
			return;
		}
		if(!msg.trim().startsWith("<")) {
			return;
		}
		
		try {
			SAXBuilder builder = new SAXBuilder();
			Document document = builder.build(new StringReader(msg));
			
			Element root = document.getRootElement();
			String type = root.getChildText("responseFormat");
			String response = root.getChildText("response");
			
			if(type == null || response == null) {
				System.err.println("Invalid response from server" + msg);
				return;
			}
			
			switch(type) {
				case "text" -> textView(response);
				
				case "image" -> {
					Image img = decodeImage(response);
					imageView(img);
				}
				
				case "audio" -> {
					playAudio(response);
				}
			}
		}
		catch(Exception e) {
			System.err.println("XML parser error" + msg);
			e.printStackTrace();
			return;
		}
	}
	
	/**
	 * Decodes the text of binary data to a image.
	 * 
	 * @param image a string of binary data 
	 * @return a new image
	 * */
	private Image decodeImage(String image) {
		byte[] img = Base64.getDecoder().decode(image);
		return new Image(new ByteArrayInputStream(img));
	}
	
	/**
	 * Creates a view for the violation text label.
	 * 
	 * @param msg message from server.
	 * */
	private void textView(String msg) {
		Platform.runLater(() -> {
			Label message = new Label(msg);
			message.setStyle(
					"-fx-background-color: rgb(0,0,0,0.8;"
					+ "-fx-text-fill: White;"
					+ "-fx-padding: 15;"
					+ "-fx-font-size: 18;"
					+ "-fx-background-radius: 10;");
			StackPane root = (StackPane) primaryStage.getScene().getRoot();
			root.getChildren().add(message);
			
			FadeTransition fade = new FadeTransition(Duration.seconds(2.5), message);
			
			fade.setFromValue(1.0);
			fade.setToValue(0.0);
			fade.setOnFinished(e -> root.getChildren().remove(message));
			fade.play();
		});
	}
	
	/**
	 * Creates a view for the image to be displayed in.
	 * Shows in front as a overlay.
	 * 
	 * @param image image from server.
	 * */
	private void imageView(Image image) {
		Platform.runLater(() -> {
			Stage overlay = new Stage();
			overlay.initStyle(javafx.stage.StageStyle.TRANSPARENT);
			overlay.setAlwaysOnTop(true);
			
			ImageView view = new ImageView(image);
			view.setOpacity(0.8);
			
			StackPane pane = new StackPane(view);
			pane.setStyle("-fx-background-color: transparent;");
			
			Scene root = new Scene(pane);
			root.setFill(Color.TRANSPARENT);
			
			overlay.setScene(root);
			
			overlay.setWidth(700);
			overlay.setHeight(700);
			
			overlay.setOnShown(e -> primaryStage.requestFocus());
			
			overlay.show();
			
			ScaleTransition scale = new ScaleTransition(Duration.seconds(0.3));
			scale.setFromY(1.0);
			scale.setFromX(1.0);
			scale.setToY(1.25);
			scale.setToX(1.25);
			scale.setAutoReverse(true);
			scale.setCycleCount(Animation.INDEFINITE);
			scale.play();
			
			PauseTransition wait = new PauseTransition(Duration.seconds(3.0));
			wait.setOnFinished(e -> {
				scale.stop();
				overlay.close();
			});
			wait.play();
		});
	}
	
	/**
	 * Receives a sound from server and plays the sound.
	 * 
	 * @param audio sound from server.
	 * @throw exception on error.
	 * */
	private void playAudio(String audio) {
		Platform.runLater(() -> {
			try {
				byte[] decode = Base64.getDecoder().decode(audio);
				
				Path temp = Files.createTempFile("sound", ".mp3");
				Files.write(temp,  decode);
				
				Media media = new Media(temp.toUri().toString());
				MediaPlayer player = new MediaPlayer(media);
				
				player.play();
			}
			catch(Exception e) {
				e.printStackTrace();
			}
		});
	}
	
	/**
	 * Main entry point launches the application.
	 * */
	public static void main(String[] args) {
		launch(args);
	}
}
