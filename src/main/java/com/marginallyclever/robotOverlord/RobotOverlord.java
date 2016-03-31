package com.marginallyclever.robotOverlord;


import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseMotionListener;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.nio.IntBuffer;
import java.util.prefs.Preferences;

import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.media.opengl.GL;
import javax.media.opengl.GL2;
import javax.media.opengl.GLAutoDrawable;
import javax.media.opengl.GLCapabilities;
import javax.media.opengl.GLEventListener;
import javax.media.opengl.GLPipelineFactory;
import javax.media.opengl.awt.GLJPanel;
import javax.media.opengl.glu.GLU;

import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.util.Animator;
import com.marginallyclever.robotOverlord.Camera.Camera;
import com.marginallyclever.robotOverlord.world.World;

/**
 * MainGUI is the root window object.
 * @author danroyer
 *
 */
public class RobotOverlord 
implements ActionListener, MouseListener, MouseMotionListener, GLEventListener
{
	static final String APP_TITLE = "Evil Overlord";
	static final String APP_URL = "https://github.com/i-make-robots/Evil-Overlord";
	
	// select picking
	static final int SELECT_BUFFER_SIZE=256;
	protected IntBuffer selectBuffer = null;
	protected boolean pickNow=false;
	protected double pickX, pickY;
	
	static final long serialVersionUID=1;
	/// used for checking the application version with the github release, for "there is a new version available!" notification
	static final String version="2";

    /// the world within the simulator and all that it contains.
	protected World world = null;

	// menus
    /// main menu bar
	protected JMenuBar mainMenu;
	/// load a new world
	protected JMenuItem buttonNew;
    /// show the load level dialog
	protected JMenuItem buttonLoad;
    /// show the save level dialog
	protected JMenuItem buttonSave;
    /// show the about dialog
	protected JMenuItem buttonAbout;
    /// check the version against github and notify the user if they wer up to date or not
	protected JMenuItem buttonCheckForUpdate;
    /// quit the application
	protected JMenuItem buttonQuit;
	
	
	/// The main frame of the GUI
    protected JFrame frame; 
    /// The animator keeps things moving
    protected Animator animator = new Animator();
    
    /* timing for animations */
    protected long start_time;
    protected long last_time;

    private float frame_delay=0;
    private float frame_length=1.0f/30.0f;
    
	// settings
    protected Preferences prefs;
	protected String[] recentFiles = {"","","","","","","","","",""};

	protected boolean checkStackSize=false;
	
	// the main view
	protected Splitter splitLeftRight;
	protected GLJPanel glCanvas;
	protected JScrollPane contextMenu;

	protected GLU glu = new GLU();
	

	public JFrame GetMainFrame() {
		return frame;
	}
	
	
	protected RobotOverlord() {
		prefs = Preferences.userRoot().node("Evil Overlord");
/*
		try {
			String s = getPath(this.getClass());
			System.out.println("enumerating "+s);
			EnumerateJarContents(s);
		}
		catch(IOException e) {
			System.out.println("failed to enumerate");
		}
*/
		
		LoadConfig();
		
        frame = new JFrame( APP_TITLE ); 
        frame.setSize( 1224, 768 );
        frame.setLayout(new java.awt.BorderLayout());

        mainMenu = new JMenuBar();
        frame.setJMenuBar(mainMenu);

        
        final Animator animator = new Animator();
        frame.addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowClosing(java.awt.event.WindowEvent e) {
              // Run this on another thread than the AWT event queue to
              // make sure the call to Animator.stop() completes before
              // exiting
              new Thread(new Runnable() {
                  public void run() {
                    animator.stop();
                    System.exit(0);
                  }
                }).start();
            }
          });
        

        GLCapabilities caps = new GLCapabilities(null);
        caps.setSampleBuffers(true);
        caps.setHardwareAccelerated(true);
        caps.setNumSamples(4);
        glCanvas = new GLJPanel(caps);

        animator.add(glCanvas);
        glCanvas.addGLEventListener(this);
        glCanvas.addMouseListener(this);
        glCanvas.addMouseMotionListener(this);
        
        contextMenu = new JScrollPane();

        splitLeftRight = new Splitter(JSplitPane.HORIZONTAL_SPLIT);
        splitLeftRight.add(glCanvas);
        splitLeftRight.add(contextMenu);

        world = new World();
        pickCamera();

        updateMenu();
        
//		frame.addKeyListener(this);
//		glCanvas.addMouseListener(this);
//		glCanvas.addMouseMotionListener(this);
//		frame.setFocusable(true);
//		frame.requestFocusInWindow();
/*
		// focus not returning after modal dialog boxes
		// http://stackoverflow.com/questions/5150964/java-keylistener-does-not-listen-after-regaining-focus
		frame.addFocusListener(new FocusListener(){
            public void focusGained(FocusEvent e){
                //System.out.println("Focus GAINED:"+e);
            }
            public void focusLost(FocusEvent e){
                //System.out.println("Focus LOST:"+e);

                // FIX FOR GNOME/XWIN FOCUS BUG
                e.getComponent().requestFocus();
            }
        });
*/
        frame.add(splitLeftRight);
        frame.validate();
        frame.setVisible(true);
        animator.start();

        last_time = start_time = System.currentTimeMillis();
    }
	
	public void setContextMenu(JPanel panel,String title) {
		JPanel container = new JPanel(new GridBagLayout());
		GridBagConstraints c = new GridBagConstraints();
		c.weighty=0;
		c.weightx=1;
		c.gridy=0;
		c.gridx=0;
		c.fill=GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.NORTHWEST;
        container.add(new JLabel(title,JLabel.CENTER),c);
        c.gridy++;
        container.add(new JSeparator(),c);
		c.weighty=1;
		c.gridy++;
        container.add(panel,c);
        
        contextMenu.setViewportView(container);
	}
	
	// see http://www.javacoffeebreak.com/text-adventure/tutorial3/tutorial3.html
	void loadWorldFromFile(String filename) {
		FileInputStream fin=null;
		ObjectInputStream objectIn=null;
		try {
			// Create a file input stream
			fin = new FileInputStream(filename);
	
			// Create an object input stream
			objectIn = new ObjectInputStream(fin);
	
			// Read an object in from object store, and cast it to a GameWorld
			this.world = (World) objectIn.readObject();
			updateMenu();
		} catch(IOException e) {
			System.out.println("World load failed (file io).");
			e.printStackTrace();
		} catch(ClassNotFoundException e) {
			System.out.println("World load failed (class not found)");
			e.printStackTrace();
		} finally {
			if(objectIn!=null) {
				try {
					objectIn.close();
				} catch(IOException e) {}
			}
			if(fin!=null) {
				try {
					fin.close();
				} catch(IOException e) {}
			}
		}
	}

	// see http://www.javacoffeebreak.com/text-adventure/tutorial3/tutorial3.html
	void saveWorldToFile(String filename) {
		FileOutputStream fout=null;
		ObjectOutputStream objectOut=null;
		try {
			fout = new FileOutputStream(filename);
			objectOut = new ObjectOutputStream(fout);
			objectOut.writeObject(world);
		} catch(IOException e) {
			System.out.println("World save failed.");
			e.printStackTrace();
		} finally {
			if(objectOut!=null) {
				try {
					objectOut.close();
				} catch(IOException e) {}
			}
			if(fout!=null) {
				try {
					fout.close();
				} catch(IOException e) {}
			}
		}
	}
	
	void saveWorldDialog() {
		JFileChooser fc = new JFileChooser();
		int returnVal = fc.showSaveDialog(this.GetMainFrame());
		if (returnVal == JFileChooser.APPROVE_OPTION) {
            saveWorldToFile(fc.getSelectedFile().getAbsolutePath());
		}
	}
	
	void loadWorldDialog() {
		JFileChooser fc = new JFileChooser();
		int returnVal = fc.showOpenDialog(this.GetMainFrame());
		if (returnVal == JFileChooser.APPROVE_OPTION) {
            loadWorldFromFile(fc.getSelectedFile().getAbsolutePath());
		}
	}
	
	void newWorld() {
		this.world = new World();
		pickCamera();
		updateMenu();
	}
	
	/*
	
	// stuff for trying to find and load plugins, part of future expansion
	 
	private String getPath(Class cls) {
	    String cn = cls.getName();
	    //System.out.println("cn "+cn);
	    String rn = cn.replace('.', '/') + ".class";
	    //System.out.println("rn "+rn);
	    String path = getClass().getClassLoader().getResource(rn).getPath();
	    //System.out.println("path "+path);
	    int ix = path.indexOf("!");
	    if(ix >= 0) {
	        path = path.substring(0, ix);
	    }
	    return path;
	}
	
	protected void EnumerateJarContents(String absPathToJarFile) throws IOException {
	    JarFile jarFile = new JarFile(absPathToJarFile);
	    Enumeration<JarEntry> e = jarFile.entries();
	    while (e.hasMoreElements()) {
			_EnumerateJarContents(e.nextElement());
		}
	}
	
	private static void _EnumerateJarContents(Object obj) {
       JarEntry entry = (JarEntry)obj;
       String name = entry.getName();
       long size = entry.getSize();
       long compressedSize = entry.getCompressedSize();
       System.out.println(name + "\t" + size + "\t" + compressedSize);
     }
	
	// Load a class from a Jar file.
	// @param absPathToJarFile c:\some\path\myfile.jar
	// @param className like com.mypackage.myclass
	protected void LoadClasses(String absPathToJarFile,String className) {
		File file  = new File(absPathToJarFile);
		try {
			URL url = file.toURI().toURL();  
			URL[] urls = new URL[]{url};
			ClassLoader cl = new URLClassLoader(urls);
			Class cls = cl.loadClass(className);
		}
		catch(MalformedURLException e) {}
		catch(ClassNotFoundException e) {}
	}
	*/
		
	public void updateMenu() {
		mainMenu.removeAll();
		
        JMenu menu = new JMenu(APP_TITLE);
        
        	buttonNew = new JMenuItem("New",KeyEvent.VK_N);
        	buttonNew.addActionListener(this);
	        menu.add(buttonNew);
        	
        	buttonLoad = new JMenuItem("Load...",KeyEvent.VK_L);
        	buttonLoad.addActionListener(this);
	        menu.add(buttonLoad);

        	buttonSave = new JMenuItem("Save As...",KeyEvent.VK_S);
        	buttonSave.addActionListener(this);
	        menu.add(buttonSave);

	        menu.add(new JSeparator());
	        
            buttonAbout = new JMenuItem("About",KeyEvent.VK_A);
	        buttonAbout.getAccessibleContext().setAccessibleDescription("About this program");
	        buttonAbout.addActionListener(this);
	        menu.add(buttonAbout);
	        
	        buttonCheckForUpdate = new JMenuItem("Check for update",KeyEvent.VK_U);
	        buttonCheckForUpdate.addActionListener(this);
	        menu.add(buttonCheckForUpdate);
	        
	        buttonQuit = new JMenuItem("Quit",KeyEvent.VK_Q);
	        buttonQuit.getAccessibleContext().setAccessibleDescription("Goodbye...");
	        buttonQuit.addActionListener(this);
	        menu.add(buttonQuit);
        
        mainMenu.add(menu);
        
        mainMenu.add(world.updateMenu());
        
        mainMenu.updateUI();
    }
	
	
	public void CheckForUpdate() {
		try {
		    // Get Github info?
			URL github = new URL("https://www.marginallyclever.com/other/software-update-check.php?id=3");
	        BufferedReader in = new BufferedReader(new InputStreamReader(github.openStream()));

	        String inputLine;
	        if((inputLine = in.readLine()) != null) {
	        	if( inputLine.compareTo(version) !=0 ) {
	        		JOptionPane.showMessageDialog(null,"A new version of this software is available.  The latest version is "+inputLine+"\n"
	        											+"Please visit http://www.marginallyclever.com/ to get the new hotness.");
	        	} else {
	        		JOptionPane.showMessageDialog(null,"This version is up to date.");
	        	}
	        } else {
	        	throw new Exception();
	        }
	        in.close();
		} catch (Exception e) {
    		JOptionPane.showMessageDialog(null,"Sorry, I failed.  Please visit http://www.marginallyclever.com/ to check yourself.");
		}
	}
	
	public void actionPerformed(ActionEvent e) {
		Object subject = e.getSource();
		
		if( subject == buttonNew ) {
			this.newWorld();
			return;
		}
		if( subject == buttonLoad ) {
			this.loadWorldDialog();
			return;
		}
		if( subject == buttonSave ) {
			this.saveWorldDialog();
			return;
		}
		if( subject == buttonAbout ) {
			JOptionPane.showMessageDialog(null,"<html><body>"
					+"<h1>"+APP_TITLE+" v"+version+"</h1>"
					+"<h3><a href='http://www.marginallyclever.com/'>http://www.marginallyclever.com/</a></h3>"
					+"<p>Created by Dan Royer (dan@marginallyclever.com).</p><br>"
					+"<p>To get the latest version please visit<br><a href='"+APP_URL+"'>"+APP_URL+"</a></p><br>"
					+"<p>This program is open source and free.  If this was helpful<br> to you, please buy me a thank you beer through Paypal.</p>"
					+"</body></html>");
			return;
		}
		if( subject == buttonCheckForUpdate ) {
			CheckForUpdate();
			return;
		}
		if( subject == buttonQuit ) {
			System.exit(0);
			return;
		}
		
		/*
		if(GeneratorMenuAction(e)) {
			return;
		}
		
		// Draw
		if( subject == buttonStart ) {
			world.robot0.Start();
			return;
		}
		if( subject == buttonStartAt ) {
			int lineNumber =getStartingLineNumber();
			if(lineNumber>=0) {
				world.robot0.StartAt(lineNumber);
			}
			return;
			
		}
		if( subject == buttonPause ) {
			world.robot0.Pause();
		}
		if( subject == buttonHalt ) {
			world.robot0.Halt();
			return;
		}
		*/
	}
	

	/**
	 * open a dialog to ask for the line number.
	 * @return true if "ok" is pressed, false if the window is closed any other way.
	 *//*
	private int getStartingLineNumber() {
		int lineNumber=-1;
		
		// TODO replace with a more elegant dialog.  See Makelangelo converters for examples.
		JPanel driver = new JPanel(new GridBagLayout());		
		JTextField starting_line = new JTextField("0",8);
		GridBagConstraints c = new GridBagConstraints();
		c.gridwidth=2;	c.gridx=0;  c.gridy=0;  driver.add(new JLabel("Start at line"),c);
		c.gridwidth=2;	c.gridx=2;  c.gridy=0;  driver.add(starting_line,c);
			    	    
	    int result = JOptionPane.showConfirmDialog(null, driver, "Start at line", 
	    		JOptionPane.OK_CANCEL_OPTION,
	    		JOptionPane.PLAIN_MESSAGE);
	    if (result == JOptionPane.OK_OPTION) {
			lineNumber=Integer.decode(starting_line.getText());
	    }
	    
		return lineNumber;
	}*/

	protected void LoadConfig() {
		GetRecentFiles();
	}

	protected void SaveConfig() {
		GetRecentFiles();
	}
	
	/*
	protected boolean GeneratorMenuAction(ActionEvent e) {
		Object subject = e.getSource();
		
        for(int i=0;i<generators.length;++i) {
        	if(subject==generatorButtons[i]) {
        		generators[i].Generate();
        		updateMenu();
        		return true;
        	}
		}
		return false;
	}
	*/
	
	
	/**
	 * changes the order of the recent files list in the File submenu, saves the updated prefs, and refreshes the menus.
	 * @param filename the file to push to the top of the list.
	 */
	public void UpdateRecentFiles(String filename) {
		int cnt = recentFiles.length;
		String [] newFiles = new String[cnt];
		
		newFiles[0]=filename;
		
		int i,j=1;
		for(i=0;i<cnt;++i) {
			if(!filename.equals(recentFiles[i]) && recentFiles[i] != "") {
				newFiles[j++] = recentFiles[i];
				if(j == cnt ) break;
			}
		}

		recentFiles=newFiles;

		// update prefs
		for(i=0;i<cnt;++i) {
			if( recentFiles[i]==null ) recentFiles[i] = new String("");
			if( recentFiles[i].isEmpty()==false ) {
				prefs.put("recent-files-"+i, recentFiles[i]);
			}
		}
		
		updateMenu();
	}
	
	// A file failed to load.  Remove it from recent files, refresh the menu bar.
	public void RemoveRecentFile(String filename) {
		int i;
		for(i=0;i<recentFiles.length-1;++i) {
			if(recentFiles[i]==filename) {
				break;
			}
		}
		for(;i<recentFiles.length-1;++i) {
			recentFiles[i]=recentFiles[i+1];
		}
		recentFiles[recentFiles.length-1]="";

		// update prefs
		for(i=0;i<recentFiles.length;++i) {
			if(!recentFiles[i].isEmpty()) {
				prefs.put("recent-files-"+i, recentFiles[i]);
			}
		}
		
		updateMenu();
	}
	
	// Load recent files from prefs
	public void GetRecentFiles() {
		int i;
		for(i=0;i<recentFiles.length;++i) {
			recentFiles[i] = prefs.get("recent-files-"+i, recentFiles[i]);
		}
	}

	/**
	 * Open a gcode file to run on a robot.  This doesn't make sense if there's more than one robot!
	 * @param filename the file to open
	 */
	public void OpenFile(String filename) {
		
	}

    @Override
    public void reshape( GLAutoDrawable drawable, int x, int y, int width, int height ) {
    	GL2 gl2 = drawable.getGL().getGL2();
        gl2.setSwapInterval(1);

    	gl2.glMatrixMode(GL2.GL_PROJECTION);
		gl2.glLoadIdentity();
		setPerspectiveMatrix();
        
        world.setup( gl2 );
    }
    
    
    @Override
    public void init( GLAutoDrawable drawable ) {
    	// Use debug pipeline
    	boolean glDebug=false;
    	boolean glTrace=false;
    	
        GL gl = drawable.getGL();
        
        if(glDebug) {
            try {
                // Debug ..
                gl = gl.getContext().setGL( GLPipelineFactory.create("javax.media.opengl.Debug", null, gl, null) );
            } catch (Exception e) {e.printStackTrace();}
        }

        if(glTrace) {
            try {
                // Trace ..
                gl = gl.getContext().setGL( GLPipelineFactory.create("javax.media.opengl.Trace", null, gl, new Object[] { System.err } ) );
            } catch (Exception e) {e.printStackTrace();}
        }

    }
    
    
    @Override
    public void dispose( GLAutoDrawable drawable ) {
    }
    
    
    @Override
    public void display( GLAutoDrawable drawable ) {
        long now_time = System.currentTimeMillis();
        float dt = (now_time - last_time)*0.001f;
    	last_time = now_time;
    	//System.out.println(dt);
    	
		// Clear The Screen And The Depth Buffer
    	GL2 gl2 = drawable.getGL().getGL2();
        
    	if(frame_delay<frame_length) {
    		frame_delay+=dt;
    	} else {
    		if(checkStackSize) {
	    		IntBuffer v = IntBuffer.allocate(1);
	    		gl2.glGetIntegerv (GL2.GL_MODELVIEW_STACK_DEPTH,v);
	    		System.out.print("start = "+v.get(0));
    		}		
	        // draw the world
    		if( world !=null ) {
    			world.render( gl2, frame_length );
    		}
	        frame_delay-=frame_length;

	        if(pickNow) {
		        pickNow=false;
		        pickIntoWorld(gl2);
    		}
			
    		if(checkStackSize) {
	    		IntBuffer v = IntBuffer.allocate(1);
				gl2.glGetIntegerv (GL2.GL_MODELVIEW_STACK_DEPTH,v);
				System.out.println(" end = "+v.get(0));
    		}
    	}
    	frame_delay+=dt;
    }

    
    protected void setPerspectiveMatrix() {
        glu.gluPerspective(60, (float)glCanvas.getSurfaceWidth()/(float)glCanvas.getSurfaceHeight(), 1.0f, 1000.0f);
    }
    
    /**
     * Use glRenderMode(GL_SELECT) to ray pick the item under the cursor.
     * https://github.com/sgothel/jogl-demos/blob/master/src/demos/misc/Picking.java
     * http://web.engr.oregonstate.edu/~mjb/cs553/Handouts/Picking/picking.pdf
     * @param gl2
     */
    protected void pickIntoWorld(GL2 gl2) {
    	// set up the buffer that will hold the names found under the cursor in furthest to closest.
        selectBuffer = Buffers.newDirectIntBuffer(RobotOverlord.SELECT_BUFFER_SIZE);
        gl2.glSelectBuffer(SELECT_BUFFER_SIZE, selectBuffer);
        // change the render mode
		gl2.glRenderMode( GL2.GL_SELECT );
		// wipe the select buffer
		gl2.glInitNames();
        // get the current viewport dimensions to set up the projection matrix
        int[] viewport = new int[4];
		gl2.glGetIntegerv(GL2.GL_VIEWPORT,viewport,0);
		// Set up a tiny viewport that only covers the area behind the cursor.  Tiny viewports are faster?
        gl2.glMatrixMode(GL2.GL_PROJECTION);
        gl2.glPushMatrix();
        gl2.glLoadIdentity();
		glu.gluPickMatrix(pickX, viewport[3]-pickY, 5.0, 5.0, viewport,0);
		setPerspectiveMatrix();

        // render in selection mode, without advancing time in the simulation.
        world.render( gl2, 0 );

        // return the projection matrix to it's old state.
        gl2.glMatrixMode(GL2.GL_PROJECTION);
        gl2.glPopMatrix();
        gl2.glFlush();

        // get the picking results and return the render mode to the default 
        int hits = gl2.glRenderMode( GL2.GL_RENDER );

        boolean pickFound=false;
        if(hits!=0) {
        	int index=0;
        	int i;
        	for(i=0;i<hits;++i) {
        		int names=selectBuffer.get(index++);
//                float z1 = (float) (selectBuffer.get(index++) & 0xffffffffL) / (float)0x7fffffff;
//                float z2 = (float) (selectBuffer.get(index++) & 0xffffffffL) / (float)0x7fffffff;
        		selectBuffer.get(index++); // near z
        		selectBuffer.get(index++); // far z
//                System.out.println("zMin:"+z1);
//                System.out.println("zMaz:"+z2);
//    			System.out.println("names:"+names);
    			if(names>0) {
        			int name = selectBuffer.get(index++);
    				ObjectInWorld newObject = world.pickObjectWithName(name);
    				setContextMenu(newObject.buildPanel(this),newObject.getDisplayName());
   					pickFound=true;
                	return;
        		}
        	}
        }
        if(pickFound==false) {
        	pickCamera();
        }
        
    }
	
	public void pickCamera() {
		Camera camera = world.getCamera();
		if(camera!=null) {
			setContextMenu(camera.buildPanel(this),camera.getDisplayName());
		}
	}

	@Override
	public void mouseClicked(MouseEvent e) {
		pickX=e.getX();
		pickY=e.getY();
		pickNow=true;
	}

	@Override
	public void mousePressed(MouseEvent e) {}
	@Override
	public void mouseReleased(MouseEvent e) {}
	@Override
	public void mouseEntered(MouseEvent e) {}
	@Override
	public void mouseExited(MouseEvent e) {}


	@Override
	public void mouseDragged(MouseEvent e) {}
	@Override
	public void mouseMoved(MouseEvent e) {}
	

	public static void main(String[] argv) {
	    //Schedule a job for the event-dispatching thread:
	    //creating and showing this application's GUI.
	    javax.swing.SwingUtilities.invokeLater(new Runnable() {
	        public void run() {
	        	new RobotOverlord();
	        }
	    });
	}
}