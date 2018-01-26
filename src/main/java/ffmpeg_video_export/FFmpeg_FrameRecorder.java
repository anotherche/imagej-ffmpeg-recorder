package ffmpeg_video_export;


import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.FrameRecorder.Exception;
import org.bytedeco.javacv.Java2DFrameConverter;

import static org.bytedeco.javacpp.avutil.*;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.GenericDialog;
import ij.io.SaveDialog;
import ij.plugin.CanvasResizer;
import ij.plugin.filter.PlugInFilter;
import ij.process.ImageProcessor;


public class FFmpeg_FrameRecorder implements AutoCloseable, PlugInFilter {
	
	private String filePath;
		
	private Java2DFrameConverter converter;
	private FFmpegFrameRecorder recorder;
	private int firstSlice, lastSlice;
	private int frameWidth, videoWidth, desiredWidth;
	private int frameHeight, videoHeight, frameHeightBorder=0;
	private int bitRate;
	private double fps=25;
	
	private	ImagePlus imp;
	private	ImageStack stack;
	
	private	boolean displayDialog = true;
	private	boolean progressByStackUpdate = false;
	private	boolean initiated = false;
	
	private Frame frame_ARGB;
	

	public FFmpeg_FrameRecorder(){
		
	}
	
	public FFmpeg_FrameRecorder(String path, ImagePlus imp){
		filePath = path;
		this.imp=imp;
		frameWidth = imp.getWidth();
    	frameHeight = imp.getHeight();
    	stack = imp.getStack();
	}
	
	public int setup(String arg, ImagePlus imp) {
    	this.imp = imp;
        return DOES_RGB + STACK_REQUIRED + NO_CHANGES;
	}

	
	public void run(ImageProcessor ip) {

		stack = imp.getStack();
		
		SaveDialog	sd = new SaveDialog("Save Video File As", "videofile", ".avi" );
		String fileName = sd.getFileName();
		if (fileName == null) return;
		String fileDir = sd.getDirectory();
		filePath = fileDir + fileName;
		frameWidth = imp.getWidth();
    	frameHeight = imp.getHeight();
		if (displayDialog && !showDialog())					//ask for parameters
			return;
		RecordVideo(filePath);
		
	}
	
	
	
	public boolean InitRecoder(int vWidth, int vHeight, boolean scaleByWidth, 
								double frameRate, int bRate) {
		
		if (stack==null || stack.getSize() < 2 || stack.getProcessor(1)==null) {
			IJ.showMessage("Error", "Nothing to encode as video.\n Stack is required with at least 2 slices.");
			initiated = false;
			return false;
		}
		
    	if (vWidth<8){
    		IJ.log("Incorrect output width");
    		initiated = false;
    		return false;
    	}
    		
    	videoWidth=vWidth + (vWidth%8==0?0:(8-vWidth%8));
    	if (scaleByWidth) {
    		int videoHeightProp = (frameHeight*videoWidth)/frameWidth;
    		int videoHeightBorder = videoHeightProp%8==0?0:(8-videoHeightProp%8);
    		videoHeight = videoHeightProp + videoHeightBorder;
    		frameHeightBorder = (videoHeightBorder*frameWidth)/videoWidth;
    	} else {
    		videoHeight=vHeight + (vHeight%8==0?0:(8-vHeight%8));
    	}
    	if (videoHeight<8){
    		IJ.log("Incorrect output height");
    		initiated = false;
    		return false;
    	}
    	
		frame_ARGB = null;
		converter = new Java2DFrameConverter();
		recorder = new FFmpegFrameRecorder(filePath, videoWidth, videoHeight);
		recorder.setVideoCodec(avcodec.AV_CODEC_ID_MPEG4);
		recorder.setFormat("avi");
		recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
		recorder.setFrameRate(fps=frameRate); 
		recorder.setVideoBitrate(bitRate=bRate);
		recorder.setGopSize(10);
		try {
			recorder.start();
		} catch (org.bytedeco.javacv.FrameRecorder.Exception e2) {
			try {
				initiated = false;
				recorder.release();
			} catch (Exception e) {
				e.printStackTrace();
			}
			IJ.log("FFmpeg encoder not starting for some reason");
			e2.printStackTrace();
			return false;
		}
		initiated = true;
		return true;
	}
	
	public void StopRecorder(){
		try {
			recorder.close();
		} catch (org.bytedeco.javacv.FrameRecorder.Exception e) {
			e.printStackTrace();
		}
	}
	
	public void EncodeFrame(ImageProcessor ip){
		if (frameHeightBorder!=0) frame_ARGB = 
				converter.convert(((new CanvasResizer()).expandImage(
					ip, frameWidth, frameHeight+frameHeightBorder, 0, frameHeightBorder/2)).getBufferedImage());
		else frame_ARGB = converter.convert(ip.getBufferedImage());
		try {
			recorder.recordImage(frame_ARGB.imageWidth, frame_ARGB.imageHeight, Frame.DEPTH_UBYTE, 
					4, frame_ARGB.imageStride, AV_PIX_FMT_ARGB, frame_ARGB.image);
		} catch (org.bytedeco.javacv.FrameRecorder.Exception e1) {
			e1.printStackTrace();
		}
	}
	
	public void RecordVideo(String path){
		
		if (!InitRecoder(desiredWidth, 0, true, fps, bitRate)) return;
		
		for (int i=firstSlice; i<lastSlice+1; i++) {
			EncodeFrame(stack.getProcessor(i));
			IJ.showProgress((i-firstSlice+1.0)/(lastSlice-firstSlice+1.0));
			if (progressByStackUpdate) imp.setSlice(i);
		}
		
		StopRecorder();
		
	}
	
	

	
	

	/** Parameters dialog, returns false on cancel */
	private boolean showDialog () {
		
//		if (!IJ.isMacro()) {
//			convertToGray = staticConvertToGray;
//			flipVertical = staticFlipVertical;
//			
//		}
		
		GenericDialog gd = new GenericDialog("AVI Recorder");
		gd.addMessage("Set the rage of stack to export to video.");
		gd.addNumericField("First slice", 1, 0);
		gd.addNumericField("Last slice", stack.getSize(), 0);
		gd.addMessage("Set width of the output video.\n"+
						"Output heigth will be scaled proportional\n"+
						"(both dimensions will be aligned to 8 pixels)");
		gd.addNumericField("Video frame width" , frameWidth, 0);
		gd.addMessage("Specify frame rate in frames per second");
		gd.addNumericField("Frame rate" , 25.0, 3);
		gd.addMessage("Specify bitrate of the compressed video (in kbps)");
		int mult = frameWidth*frameHeight/614400;
		gd.addNumericField("Video bitrate" , (mult<1?1:mult)*1024, 0);
		gd.addCheckbox("Show progress by stack update (slows down the conversion)", false);
		gd.pack();
		//gd.addCheckbox("Convert to Grayscale", convertToGray);
		//gd.addCheckbox("Flip Vertical", flipVertical);
		
		gd.setSmartRecording(true);
		gd.showDialog();
		if (gd.wasCanceled()) return false;
		//convertToGray = gd.getNextBoolean();
		//flipVertical = gd.getNextBoolean();
//		if (!IJ.isMacro()) {
//			staticConvertToGray = convertToGray;
//			staticFlipVertical = flipVertical;
//		}
		firstSlice = (int)Math.abs(gd.getNextNumber());
		lastSlice = (int)Math.abs(gd.getNextNumber());
		if (firstSlice<1) firstSlice=1;
		if (lastSlice>stack.getSize()) lastSlice=stack.getSize();
		if (lastSlice<=firstSlice) {
			IJ.showMessage("Error", "Incorrect slice range");
			return false;
		}
		desiredWidth=(int)Math.abs(gd.getNextNumber());
		fps=Math.abs(gd.getNextNumber());
		bitRate = (int)Math.abs(gd.getNextNumber())*1024;
		progressByStackUpdate = gd.getNextBoolean();
		
	
		IJ.register(this.getClass());
		return true;
	}
	
	public void displayDialog(boolean displayDialog) {
		this.displayDialog = displayDialog;
	}
	
	

	

	

	


	@Override
	public void close() throws java.lang.Exception {
		if (recorder!=null){
			
			try {
				recorder.close();
			} catch (FrameRecorder.Exception e) {
				
				e.printStackTrace();
			}
		}
		
	}
	
	public double getFrameRate() {
		return fps;
	}

	public boolean isInitiated() {
		return initiated;
	}

	
}
