package ffmpeg_video_export;


import java.io.OutputStream;
import java.util.ArrayList;


import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacpp.swscale;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.Java2DFrameConverter;

import static org.bytedeco.javacpp.avutil.*;

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.io.SaveDialog;
import ij.plugin.CanvasResizer;
import ij.plugin.PlugIn;
import ij.process.ImageProcessor;








public class FFmpeg_FrameRecorder implements AutoCloseable, PlugIn {
	
	private String fileDirectory;
	private String fileName;
	
	private Java2DFrameConverter converter;
	private FFmpegFrameRecorder recorder;
	private int firstSlice, lastSlice;
	private int frameWidth, videoWidth;
	private int frameHeight, videoHeight, frameHeightBorder=0;
	private int bitRate;
	private double fps=25;

	
	private	ImagePlus imp;
	private	ImageStack stack;
	
	
	private double frameRate;
	
	private	boolean displayDialog = true;
	private	boolean progressByStackUpdate = false;
	
	
	//private AVFrame av_frame_YUV420p;
	private Frame frame_ARGB;
	
	private avcodec.AVCodecContext c = null;
   
    
    private avcodec.AVPacket pkt = new avcodec.AVPacket();
    

   
    private swscale.SwsContext sws_ctx = null;
    private OutputStream stream = null;
	
	
	
	
	public void run(String arg) {
//		String options = IJ.isMacro()?Macro.getOptions():null;
//		if (options!=null && options.contains("select=") && !options.contains("open="))
//			Macro.setOptions(options.replaceAll("select=", "open="));
//		if (arg!=null && !arg.equals("") && arg.contains("importquiet=true")) displayDialog=false;
		int openImpCount = WindowManager.getWindowCount();
		ArrayList<String> stacks = new ArrayList<String>(0);
		ArrayList<Integer> stackIDs = new ArrayList<Integer>(0);

    	int seqCount=0;
    	for (int srcCnt = 0; srcCnt < openImpCount; srcCnt++) {
    		ImagePlus openImp = WindowManager.getImage(srcCnt+1);
    		if (openImp.getStack()!=null && openImp.getStack().getSize()>1){
    			stacks.add(openImp.getTitle());
    			stackIDs.add(openImp.getID());
    			seqCount++;

    		}
    		
    	}
    	
    	if (seqCount>0){
        	if (openImpCount==1){
        		imp = WindowManager.getImage(stackIDs.get(0));
				WindowManager.setCurrentWindow(this.imp.getWindow());
        	} else {
        		GenericDialog gd = new GenericDialog("Bending Crystal Track");
        		gd.addMessage("Select image sequence stack or press Cancel to open another stack");
        		gd.addChoice("List of open virtual stacks", stacks.toArray(new String[0]), stacks.get(0));
        		gd.showDialog();
        		if (!gd.wasCanceled()) {
        			imp = WindowManager.getImage(stackIDs.get(gd.getNextChoiceIndex()));
        			WindowManager.setCurrentWindow(this.imp.getWindow());
       		}
        	}
    	} else {
    		IJ.showMessage("There is no stack open.");
    		return;
    	}
		
    	frameWidth = imp.getWidth();
    	frameHeight = imp.getHeight();
		SaveDialog	sd = new SaveDialog("Save Video File As", "videofile", ".avi" );
		String fileName = sd.getFileName();
		if (fileName == null) return;
		String fileDir = sd.getDirectory();
		String path = fileDir + fileName;
		stack = imp.getStack();
		if (stack==null || stack.getSize() < 2 || stack.getProcessor(1)==null) {
			return;
		}
		if (displayDialog && !showDialog())					//ask for parameters
			return;
		RecordVideo(path);
		
	}
	
	
	
	public void RecordVideo(String path){
		frame_ARGB = null;
		converter = new Java2DFrameConverter();
		recorder = new FFmpegFrameRecorder(path, videoWidth, videoHeight);
		recorder.setVideoCodec(avcodec.AV_CODEC_ID_MPEG4);
		recorder.setFormat("avi");
		recorder.setPixelFormat(avutil.AV_PIX_FMT_YUV420P);
		recorder.setFrameRate(fps); //TODO: Don't hardcode.
		recorder.setVideoBitrate(bitRate);
		recorder.setGopSize(10);
		try {
			recorder.start();
		} catch (org.bytedeco.javacv.FrameRecorder.Exception e2) {
			e2.printStackTrace();
		}

		for (int i=firstSlice; i<lastSlice+1; i++) {
			ImageProcessor ip = stack.getProcessor(i);
			if (frameHeightBorder!=0){
				CanvasResizer cnvRes = new CanvasResizer();
				ip = cnvRes.expandImage(ip, frameWidth, frameHeight+frameHeightBorder, 0, frameHeightBorder/2);
			}
			frame_ARGB = converter.convert(ip.getBufferedImage());
			try {
				recorder.recordImage(frame_ARGB.imageWidth, frame_ARGB.imageHeight, Frame.DEPTH_UBYTE, 4, frame_ARGB.imageStride, AV_PIX_FMT_ARGB, frame_ARGB.image);
			} catch (org.bytedeco.javacv.FrameRecorder.Exception e1) {
				e1.printStackTrace();
			}
			IJ.showProgress((i-firstSlice+1.0)/(lastSlice-firstSlice+1.0));
			if (progressByStackUpdate) imp.setSlice(i);
		}
		try {
			recorder.close();
		} catch (org.bytedeco.javacv.FrameRecorder.Exception e) {
			e.printStackTrace();
		}
		
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
		videoWidth=(int)Math.abs(gd.getNextNumber());
		videoWidth+=videoWidth%8==0?0:8-videoWidth%8;
		int videoHeightProp = (frameHeight*videoWidth)/frameWidth;
		int videoHeightBorder = videoHeightProp%8==0?0:8-videoHeightProp%8;
		videoHeight = videoHeightProp + videoHeightBorder;
		frameHeightBorder = (videoHeightBorder*frameWidth)/videoWidth;
		fps=Math.abs(gd.getNextNumber());
		bitRate = (int)Math.abs(gd.getNextNumber())*1024;
		progressByStackUpdate = gd.getNextBoolean();
		
		
		IJ.register(this.getClass());
		return true;
	}
	
	public void displayDialog(boolean displayDialog) {
		this.displayDialog = displayDialog;
	}
	
	

	/** Returns the path to the directory containing the images. */
	public String getDirectory() {
		return fileDirectory;
	}

	/** Returns the file name of the specified slice, were 1<=n<=nslices. */
	public String getFileName(int n) {
		return fileName;
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
		return frameRate;
	}

}
