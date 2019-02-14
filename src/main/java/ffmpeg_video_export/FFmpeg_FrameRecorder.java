package ffmpeg_video_export;


import org.bytedeco.javacpp.avcodec;
import org.bytedeco.javacpp.avutil;
import org.bytedeco.javacv.FFmpegFrameRecorder;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.FrameRecorder;
import org.bytedeco.javacv.FrameRecorder.Exception;
import org.bytedeco.javacv.Java2DFrameConverter;
import static org.bytedeco.javacpp.avutil.*;
import static org.bytedeco.javacpp.avformat.*;

import java.awt.Button;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

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
	private int formatCode=0;
	private int codecCode=0;
	private String customVEnc;
	private ArrayList<String> vKeys; 
	private ArrayList<String> vOptions;
	private JTable vcodecOptTab;
	
	private	ImagePlus imp;
	private	ImageStack stack;
	
	private	boolean displayDialog = true;
	private	boolean progressByStackUpdate = false;
	private	boolean initialized = false;
	
	private Frame frame_ARGB;
	
	private static final String[] formats = new String[] {"guess by file extension", "avi", "mov", "mp4", "mkv"};
	private static final String[] encoders = new String[] {"by format", "mpeg4", "h264", "mjpeg", "huffyuv", "custom"};
	


	
	public int setup(String arg, ImagePlus imp) {
    	this.imp = imp;
        return DOES_ALL + STACK_REQUIRED + NO_CHANGES;
	}

	
	public void run(ImageProcessor ip) {

		stack = imp.getStack();
		ip.convertToRGB();
		SaveDialog	sd = new SaveDialog("Save Video File As", "videofile", ".avi" );
		String fileName = sd.getFileName();
		if (fileName == null) return;
		String fileDir = sd.getDirectory();
		filePath = fileDir + fileName;
		frameWidth = imp.getWidth();
    	frameHeight = imp.getHeight();
		if (displayDialog && !showDialog())					//ask for parameters
			return;
		
		RecordVideo(filePath, imp, desiredWidth, fps, bitRate, firstSlice, lastSlice);
		
	}
	
	
	String getFileExtension(String path) {
		String extension = "";
		if (path!=null && !path.isEmpty()){
			int i = path.lastIndexOf('.');
			if (i > 0 && i < path.length() - 1) 
				extension = path.substring(i+1);
		}
		    return extension;
	}

	/** Initializes and starts FFmpegFrameRecorder with default settings:
	 * framerate = 25 fps, bitrate (automatically estimated to give high quality), 
	 * video codec (MPEG-4 simple profile), video format "avi", pixel format YUV420P, 
	 * gop size = 10, and other codec options are defaults.
	 * Video frame is proportionally rescaled from the initial dimensions of srcImp 
	 * to give desired frame width.  
	 * Video frame dimensions are aligned to 8 pixel.
	 *  @param path   path to the resulting video file
	 *  @param srcImp ImagePlus instance providing initial dimensions of image
	 *  @param vWidth desired width of video frame. 
	 */
	public boolean InitRecorder(String path, ImagePlus srcImp, int vWidth){
		return initialized = InitRecorder(path, srcImp, vWidth, 25.0, 
				(int) (vWidth*vWidth*srcImp.getHeight()*1024.0/614400/srcImp.getWidth()));
	}

	/** Initializes and starts FFmpegFrameRecorder with default settings:
	 * video codec (MPEG-4 simple profile), video format "avi", pixel format YUV420P, 
	 * gop size = 10, and other codec options are defaults.
	 * Video frame is proportionally rescaled from the initial dimensions of srcImp 
	 * to give desired frame width.  
	 * Video frame dimensions are aligned to 8 pixel.
	 *  @param path   path to the resulting video file
	 *  @param srcImp ImagePlus instance providing initial dimensions of image
	 *  @param vWidth desired width of video frame. 
	 *  @param frameRate desired framerate in fps
	 *  @param bRate desired bitrate in bps
	 */
	public boolean InitRecorder(String path, ImagePlus srcImp, int vWidth, double frameRate, int bRate) {
		
		return initialized = InitRecorder(path, srcImp.getWidth(), srcImp.getHeight(), vWidth, frameRate, bRate);
	}
	
	
	/** Initializes and starts FFmpegFrameRecorder with default settings:
	 * framerate = 25 fps, bitrate (automatically estimated to give high quality), 
	 * video codec (MPEG-4 simple profile), video format "avi", pixel format YUV420P, 
	 * gop size = 10, and other codec options are defaults.
	 * Video frame is proportionally rescaled from the specified initial dimensions 
	 * to give desired frame width.  
	 * Video frame dimensions are aligned to 8 pixel.
	 *  @param path   path to the resulting video file
	 *  @param srcWidth width of initial image
	 *  @param srcHeight height of initial image
	 *  @param vWidth desired width of video frame. 
	 */
	public boolean InitRecorder(String path, int srcWidth, int srcHeight, int vWidth) {
		return initialized = InitRecorder(path, srcWidth, srcHeight, vWidth, 25.0, 
				(int) (vWidth*vWidth*srcHeight*1024.0/614400/srcWidth));
	}
	
	
	/** Initializes and starts FFmpegFrameRecorder with default settings:
	 * video codec (MPEG-4 simple profile), video format "avi", pixel format YUV420P, 
	 * gop size = 10, and other codec options are defaults.
	 * Video frame is proportionally rescaled from the specified initial dimensions 
	 * to give desired frame width.  
	 * Video frame dimensions are aligned to 8 pixel.
	 *  @param path   path to the resulting video file
	 *  @param srcWidth width of initial image
	 *  @param srcHeight height of initial image
	 *  @param vWidth desired width of video frame. 
	 *  @param frameRate desired framerate in fps
	 *  @param bRate desired bitrate in bps
	 */
	public boolean InitRecorder(String path, int srcWidth, int srcHeght, int vWidth, double frameRate, int bRate) {
		if (vWidth<8){
			IJ.log("Incorrect output width");
			initialized = false;
			return false;
		}
		
		if (srcWidth<8 || srcHeght<8){
			IJ.log("Incorrect source dimentions");
			initialized = false;
			return false;
		}
		frameWidth = srcWidth;
		frameHeight = srcHeght;
		videoWidth=vWidth + (vWidth%8==0?0:(8-vWidth%8));
		int videoHeightProp = (frameHeight*videoWidth)/frameWidth;
		int videoHeightBorder = videoHeightProp%8==0?0:(8-videoHeightProp%8);
		videoHeight = videoHeightProp + videoHeightBorder;
		frameHeightBorder = (videoHeightBorder*frameWidth)/videoWidth;
		if (videoHeight<8){
			IJ.log("Incorrect output height");
			initialized = false;
			return false;
		}

		return initialized = InitRecorder(path, videoWidth, videoHeight, frameRate, bRate);
	}
	
	/** Initializes and starts FFmpegFrameRecorder with default pixel format YUV420P,
	 * while other settings are customized:
	 * video format (0 is by file extension), video codec (0 is by format), 
	 * custom codec can be specified, as well as custom codec options.
	 * Video frame is proportionally rescaled from the specified initial dimensions 
	 * to give desired frame width.  
	 * Video frame dimensions are aligned to 8 pixel.
	 *  @param path   path to the resulting video file
	 *  @param srcWidth width of initial image
	 *  @param srcHeight height of initial image
	 *  @param vWidth desired width of video frame. 
	 *  @param frameRate desired framerate in fps
	 *  @param bRate desired bitrate in bps
	 *  @param format_code format code
	 *  @param vcodec index of the video codec in FFmpeg library
	 *  @param codec_code codec code 
	 *  @param vKeys a list of additional video option keys
	 *  @param vOptions a list of corresponding options  
	 */
	private boolean InitRecorder(String path, int srcWidth, int srcHeght, 
			int vWidth, double frameRate, int bRate, int format_code, int codec_code, String v_codec_custom,
			ArrayList<String> vKeys, ArrayList<String> vOptions) {
		if (vWidth<8){
			IJ.log("Incorrect output width");
			initialized = false;
			return false;
		}
		
		if (srcWidth<8 || srcHeght<8){
			IJ.log("Incorrect source dimentions");
			initialized = false;
			return false;
		}
		frameWidth = srcWidth;
		frameHeight = srcHeght;
		videoWidth=vWidth + (vWidth%8==0?0:(8-vWidth%8));
		int videoHeightProp = (frameHeight*videoWidth)/frameWidth;
		int videoHeightBorder = videoHeightProp%8==0?0:(8-videoHeightProp%8);
		videoHeight = videoHeightProp + videoHeightBorder;
		frameHeightBorder = (videoHeightBorder*frameWidth)/videoWidth;
		if (videoHeight<8){
			IJ.log("Incorrect output height");
			initialized = false;
			return false;
		}

		
		int v_codec = avcodec.AV_CODEC_ID_NONE;
		switch (codec_code) {
			case 1: 
				v_codec = avcodec.AV_CODEC_ID_MPEG4;
				break;
			case 2:
				v_codec = avcodec.AV_CODEC_ID_H264;
				break;
			case 3:
				v_codec = avcodec.AV_CODEC_ID_MJPEG;
				break;
			case 4:
				v_codec = avcodec.AV_CODEC_ID_HUFFYUV;
				break;
			case 5:
				v_codec = -1;
				break;
			
		}
		
		String vFmt = format_code == 0?getFileExtension(path).toLowerCase():formats[format_code];
		
		
		return initialized = InitRecorder(path, videoWidth, videoHeight, 
				avutil.AV_PIX_FMT_NONE, frameRate, bRate, v_codec, v_codec_custom, vFmt,
				0,  vKeys, vOptions);
	}
	
	
	
	/** Initializes and starts FFmpegFrameRecorder with default settings:
	 * framerate = 25 fps, bitrate (automatically estimated to give high quality), 
	 * video codec (MPEG-4 simple profile), video format "avi", pixel format YUV420P, 
	 * gop size = 10, and other codec options are defaults.
	 * Video frame is rescaled from dimensions of initial image   
	 * to give desired frame width and height.  
	 * Video frame dimensions are aligned to 8 pixel.
	 *  @param path   path to the resulting video file
	 *  @param vWidth desired width of video frame. 
	 *  @param vHeight desired height of video frame.
	 */
	public boolean InitRecorder(String path, int vWidth, int vHeight) {   	
		return initialized = InitRecorder(path, vWidth, vHeight, 25.0, (int) (vWidth*vHeight*1024.0/614400));
	}
	
	
	/** Initializes and starts FFmpegFrameRecorder with default settings:
	 * video codec (MPEG-4 simple profile), video format "avi", pixel format YUV420P, 
	 * gop size = 10, and other codec options are defaults.
	 * Video frame is rescaled from dimensions of initial image   
	 * to give desired frame width and height.  
	 * Video frame dimensions are aligned to 8 pixel.
	 *  @param path   path to the resulting video file
	 *  @param vWidth desired width of video frame. 
	 *  @param vHeight desired height of video frame.
	 *  @param frameRate desired framerate in fps
	 *  @param bRate desired bitrate in bps
	 */
	public boolean InitRecorder(String path, int vWidth, int vHeight, double frameRate, int bRate) {
		
		
		if (vWidth<8 || vHeight<8){
    		IJ.log("Incorrect output dimensions");
    		initialized = false;
    		return false;
    	}
    		
    	videoWidth=vWidth + (vWidth%8==0?0:(8-vWidth%8));
    	videoHeight=vHeight + (vHeight%8==0?0:(8-vHeight%8));
    	
		return initialized = InitRecorder(path, videoWidth, videoHeight, 
				avutil.AV_PIX_FMT_NONE, frameRate, bRate, avcodec.AV_CODEC_ID_MPEG4, null, "avi",
				10, null, null);
	}
	

	
	/** Initializes and starts FFmpegFrameRecorder with customized settings:
	 * Video frame dimensions are aligned to 8 pixel.
	 *  @param path   path to the resulting video file
	 *  @param vWidth desired width of video frame. 
	 *  @param vHeight desired height of video frame.
	 *  @param pixFmt pixel format of encoded frames
	 *  @param frameRate desired framerate in fps
	 *  @param bRate desired bitrate in bps
	 *  @param vcodec index of the video codec in FFmpeg library
	 *  @param vFmt format of video (avi, mkv, mp4, etc.)
	 *  @param gopSze the gop size
	 *  @param vKeys a list of additional video option keys
	 *  @param vOptions a list of corresponding options
	 */
	public boolean InitRecorder(String path, int vWidth, int vHeight, 
			int pixFmt, double frameRate, int bRate, int vcodec, String vcodecName, String vFmt,
			int gopSize, ArrayList<String> vKeys, ArrayList<String> vOptions) {
		
		if (initialized) {
			try {
				close();
			} catch (java.lang.Exception e) {
				e.printStackTrace();
			}
		}

		filePath = path;
		videoWidth = vWidth;
		videoHeight = vHeight;
		frame_ARGB = null;
		fps=frameRate;
		bitRate=bRate;
		converter = new Java2DFrameConverter();
		try {
			recorder = FFmpegFrameRecorder.createDefault(path, vWidth, vHeight);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
		
		//if (vFmt!=null && !vFmt.isEmpty()) recorder.setFormat(vFmt);
		
		/* auto detect the output format from the name. */
		AVOutputFormat oformat;
        String format_name = vFmt == null || vFmt.length() == 0 ? null : vFmt;
        if ((oformat = av_guess_format(format_name, path, null)) == null) {
            int proto = path.indexOf("://");
            if (proto > 0) {
                format_name = path.substring(0, proto);
            }
            if ((oformat = av_guess_format(format_name, path, null)) == null) {
               // throw new Exception("av_guess_format() error: Could not guess output format for \"" + path + "\" and " + vFmt + " format.");
            	IJ.showMessage("oformat error");
            }
        }
        format_name = oformat.name().getString();
		
		recorder.setFormat(format_name);
		
		if (vcodec>avcodec.AV_CODEC_ID_NONE) recorder.setVideoCodec(vcodec);
		else if (vcodec == -1) recorder.setVideoCodecName(vcodecName);
		   //if (vcodecName!=null && !vcodecName.isEmpty()) recorder.setVideoCodecName(vcodecName);
		else {
			int venc = av_guess_codec(oformat, null, path, null, AVMEDIA_TYPE_VIDEO);
//			IJ.showMessage("encoder id = "+venc);
			recorder.setVideoCodec(venc);
		}
		
		if (pixFmt>=0) recorder.setPixelFormat(pixFmt);
		if (frameRate>0) recorder.setFrameRate(frameRate); 
		if (bRate>0) recorder.setVideoBitrate(bRate);
		if (gopSize>0) recorder.setGopSize(gopSize);
		if (vKeys!=null && vOptions!=null && !vKeys.isEmpty() && vKeys.size()==vOptions.size()) 
			for (int i=0; i<vKeys.size(); i++) recorder.setVideoOption(vKeys.get(i), vOptions.get(i));
		
		try {
			recorder.start();
		} catch (org.bytedeco.javacv.FrameRecorder.Exception e2) {
			try {
				initialized = false;
				recorder.release();
			} catch (Exception e) {
				e.printStackTrace();
			}
			IJ.log("FFmpeg encoder not starting for some reason");
			e2.printStackTrace();
			return false;
		}
		initialized = true;
		return true;
	}
	
	
	
	/** Stops record and releases resources.
	 * Should be called at the end of record. 
	 */
	public void StopRecorder(){
		try {
			recorder.close();
			initialized = false;
		} catch (org.bytedeco.javacv.FrameRecorder.Exception e) {
			e.printStackTrace();
		}
	}
	
	
	/** Encodes one frame (next frame of the video)
	 * The image will be transformed to RGB if necessary, then encoded with
	 * parameters specified in a InitRecorder(...) function
	 *  @param ip ImageProcessor of the image to encode 
	 */
	public void EncodeFrame(ImageProcessor ip){
		if (frameHeightBorder!=0) frame_ARGB = 
			converter.convert(
				((new CanvasResizer()).expandImage(ip, frameWidth, frameHeight+frameHeightBorder, 
											0, frameHeightBorder/2)).convertToRGB().getBufferedImage());
		else frame_ARGB = converter.convert(ip.convertToRGB().getBufferedImage());
		
		try {
			recorder.recordImage(frame_ARGB.imageWidth, frame_ARGB.imageHeight, Frame.DEPTH_UBYTE, 
					4, frame_ARGB.imageStride, AV_PIX_FMT_ARGB, frame_ARGB.image);
		} catch (org.bytedeco.javacv.FrameRecorder.Exception e1) {
			e1.printStackTrace();
		}
	}
	
	
	/** Encodes a stack into a video with default and specified parameters 
	 * frame dimensions are resized proportionally to give the desired width
	 * The function uses standard working scheme:
	 * 1. InitRecorder
	 * 2. EncodeFrame in a cycle running through the specified stack range 
	 * 3. StopRecorder
	 *   @param path the path of the resulting video file
	 *    
	 */
	public void RecordVideo(String path, ImagePlus imp, int desiredWidth, 
							double frameRate, int bRate, int firstSlice, int lastSlice){
		if (imp==null) return;
		
		ImageStack stack = imp.getStack();
		if (stack==null || stack.getSize() < 2 || stack.getProcessor(1)==null) {
			IJ.log("Nothing to encode as video. Stack is required with at least 2 slices.");
			initialized = false;
			return;
		}
		
		if (firstSlice>lastSlice || firstSlice>stack.getSize()){
			IJ.log("Incorrect slice range");
			initialized = false;
			return;
		}
		
		//if (!InitRecorder(path, imp, desiredWidth, frameRate, bRate)) return;
		if (!InitRecorder(path, imp.getWidth(), imp.getHeight(), 
				desiredWidth, frameRate, bRate, formatCode, codecCode, customVEnc, vKeys, vOptions)) return;
		
		int start = firstSlice<0?1:firstSlice;
		int finish = lastSlice>stack.getSize()?stack.getSize():lastSlice;
		
		for (int i=start; i<finish+1; i++) {
			EncodeFrame(stack.getProcessor(i));
			IJ.showProgress((i-start+1.0)/(finish-start+1.0));
			if (progressByStackUpdate) imp.setSlice(i);
		}
		
		StopRecorder();
		IJ.log("The video encoding is complete. File path:\n"+path);
		
	}


	/** Parameters dialog, returns false on cancel */
	private boolean showDialog () {

		Integer videoOptions = new Integer(0);
		GenericDialog gd = new GenericDialog("AVI Recorder");
		gd.addMessage("Select slices to encode.");
		gd.addNumericField("First slice", 1, 0);
		gd.addNumericField("Last slice", stack.getSize(), 0);
		gd.addMessage("Set width of the output video.\n"+
						"Output heigth will be scaled proportional\n"+
						"(both dimensions will be aligned to 8 pixels)");
		gd.addNumericField("Video frame width" , frameWidth, 0);
		gd.addMessage("Specify frame rate in frames per second");
		gd.addNumericField("Frame rate" , 25.0, 3);
		gd.addMessage("Specify bitrate of the compressed video (in kbps).\n"+
					  "Set zero for automatic configuration by ffmpeg");
		int br = (int)((frameWidth*frameHeight*1024L)/614400L);
		
		gd.addNumericField("Video bitrate" , br<128?128:br, 0);
		gd.addMessage("Select output format.");
		gd.addChoice("Format", formats, formats[0]);
		gd.addMessage("Select video encoder. Default is determined by the format.\n"
				+ "Select \"custom\" to specify own.");
		gd.addChoice("Encoder", encoders, encoders[0]);
		gd.addStringField("Custom encoder", "");
		gd.addCheckbox("Show progress by stack update (slows down the conversion)", false);
		
		//grid.
		vcodecOptTab = null;
		Button btn_vopt = new Button("Add encoder option");

		final Panel optionsBtnPanel = new Panel();
		GridBagLayout grid = (GridBagLayout)gd.getLayout();
		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 2; c.gridy = 0;
		c.gridwidth = 1;
		c.gridheight = 1;
		c.anchor = GridBagConstraints.WEST;
		grid.setConstraints(optionsBtnPanel, c);
		gd.add(optionsBtnPanel);
		btn_vopt.addActionListener(new ActionListener() {

			@Override
			public void actionPerformed(ActionEvent e) {
				if (vcodecOptTab == null) {
					vcodecOptTab = new JTable(new DefaultTableModel(new Object[]{"Key", "Option"},1));
					JScrollPane scrollPane = new JScrollPane(vcodecOptTab,
                            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
					
					GridBagLayout grid = (GridBagLayout)gd.getLayout();
					GridBagConstraints c = new GridBagConstraints();
					c.gridx = 2; c.gridy = 1;
					c.gridwidth = 1;
					c.gridheight = GridBagConstraints.REMAINDER ;
					c.anchor = GridBagConstraints.NORTHWEST;
					Panel optionsPanel = new Panel(new GridLayout());
					optionsPanel.setPreferredSize(new Dimension(250,250));
					grid.setConstraints(optionsPanel, c);
					gd.add(optionsPanel);
					
					optionsPanel.add(scrollPane);
					
				} else {
					
					DefaultTableModel model = (DefaultTableModel) vcodecOptTab.getModel();
					model.addRow(new Object[]{"", ""});
				}
				
				gd.validate();
       		 	gd.repaint();
       		 	gd.pack();
				
			}
			
		});
		optionsBtnPanel.add(btn_vopt);
		gd.pack();
		gd.setSmartRecording(true);
		gd.showDialog();
		if (gd.wasCanceled()) return false;
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
		formatCode = gd.getNextChoiceIndex();
		codecCode= gd.getNextChoiceIndex();
		customVEnc = gd.getNextString();
		progressByStackUpdate = gd.getNextBoolean();
		DefaultTableModel model = vcodecOptTab == null?null:(DefaultTableModel) vcodecOptTab.getModel();
		vKeys = null;
		vOptions = null;
		if (model!=null) {
			int row = vcodecOptTab.getEditingRow();
			int col = vcodecOptTab.getEditingColumn();
			if (row != -1 && col != -1)
				vcodecOptTab.getCellEditor(row,col).stopCellEditing();
			int rowCount = model.getRowCount();
			if (rowCount>0) {
				vKeys = new ArrayList<String>(rowCount);
				vOptions = new ArrayList<String>(rowCount);

				for (int i =0; i< rowCount; i++){
					if (model.getValueAt(i, 0)!=null && !model.getValueAt(i, 0).toString().isEmpty()) {
						vKeys.add(model.getValueAt(i, 0).toString());
						if (model.getValueAt(i, 1)!=null && !model.getValueAt(i, 1).toString().isEmpty())
							vOptions.add(model.getValueAt(i, 1).toString());
						else vOptions.add(" ");
					}
					

				}
			}


		}
		

		if (formatCode > 0 && !getFileExtension(filePath).toLowerCase().equals(formats[formatCode])) {
			filePath = filePath.substring(0, filePath.lastIndexOf(getFileExtension(filePath)))+formats[formatCode];
			File videofile = new File(filePath);
			if(videofile.exists()) {
				GenericDialog rewritedlg = new GenericDialog("File exists");
				rewritedlg.setCancelLabel("NO");
				rewritedlg.addMessage("The file with the selected extension ("+formats[formatCode]+") already exists. Overvrite?\n(pressing NO cancels encoding)");
				rewritedlg.showDialog();
				if (rewritedlg.wasCanceled()) return false;
			}
			else IJ.showMessage("Format warning", "Selected format mismatches file extension.\nExtension is replaced to "+formats[formatCode]);
		}
		
			

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
				frameHeightBorder = 0;
				initialized = false;
			} catch (FrameRecorder.Exception e) {
				
				e.printStackTrace();
			}
		}
		
	}
	
	public double getFrameRate() {
		return fps;
	}

	public boolean isInitialized() {
		return initialized;
	}

	
}
