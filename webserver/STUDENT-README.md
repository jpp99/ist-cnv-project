# Cloud Computing and Virtualization - 2020/21, 2nd Semester
[Course Page: https://fenix.tecnico.ulisboa.pt/disciplinas/AVExe76/2020-2021/2-semestre/pagina-inicial](https://fenix.tecnico.ulisboa.pt/disciplinas/AVExe/2018-2019/2-semestre/pagina-inicial)

[Project Instructions: http://grupos.ist.utl.pt/meic-cnv/project/Enunciado_CNV_2020_21.pdf](http://grupos.ist.utl.pt/meic-cnv/project/Enunciado_CNV_2020_21.pdf)

[Project FAQ:   https://tinyurl.com/cnv-faq-20-21](https://tinyurl.com/cnv-faq-20-21)

--------

The Java code developed by the faculty targets Java version 7.
We provide the compiled ***.class*** files in this archive in:

	pt/
	org/
	
The ***org/*** directory is a helper library which is used by the solver code. You don't need to use or instrument the classes in that directory.

For examples in this readme, we assume the ***.zip*** archive was extracted to the user's home directory and the directory structure should look like:

    $HOME/cnv-project/datasets/
    $HOME/cnv-project/META-INF/  
    $HOME/cnv-project/org/    
    $HOME/cnv-project/pt/
    $HOME/cnv-project/STUDENT-README.md
    $HOME/ist-flag-transparent-bg.png
    $HOME/ist-flag-transparent-bg-resized.png
    
Where **$HOME** represents the user's home directory.
In Amazon EC2 instances, it should look like:

    /home/ec2-user/cnv-project/datasets/    
    /home/ec2-user/cnv-project/META-INF    
    /home/ec2-user/cnv-project/org/    
    /home/ec2-user/cnv-project/pt/
    /home/ec2-user/cnv-project/STUDENT-README.md
    /home/ec2-user/cnv-project/ist-flag-transparent-bg.png
    /home/ec2-user/cnv-project/ist-flag-transparent-bg-resized.png
    
    
    

## HTML Server

You will launch solver jobs through the faculty web page at http://groups.tecnico.ulisboa.pt/meic-cnv/cnv-project/. In this link there is a simple HTML page where you can choose the map to scan, the scan strategy (GRID_SCAN/PROGRESSIVE_SCAN/GREEDY_RANGE_SCAN) to use, the starting point (xS, yS), the top-left corner (x0, y0) and bottom-right corner (x1, y1) of the bounding-rectangle.

You must also specify the address and port of your Amazon EC2 instance or load balancer so that your browser sends the request to it and receives the solution when it finishes.

When you configure the parameters such as your Amazon EC2 instance (load balancer) address and port, the scanning strategy to use, the starting point and the bounding rectangle coordinates and the map on which to execute the solver, you can press the **Execute** button.
Your browser will then send a request to your web server running on Amazon EC2.

If you are using Google Chrome, you need to start the browser with the following flag to access the HTML page:

    google-chrome --allow-file-access-from-files

We provide an example web server which listens for requests on port 8000 - **see below for more information**.

## This code has three main Java applications:

- Sample web server (which will be run on your Amazon EC2 instances) which receives requests from the course faculty web page at http://groups.tecnico.ulisboa.pt/meic-cnv/cnv-project/
- Solver with GRID_SCAN/PROGRESSIVE_SCAN/GREEDY_RANGE_SCAN strategies (implemented as simple map covering algorithms).
- Voronoi map generator.

### Solver - pt.ulisboa.tecnico.cnv.solver.SolverMain

This is the black-box with strategies to be instrumented in the Amazon EC2 instances and measured by your code using the Java BIT instrumentation tool.


Here is an example to trigger a solver execution over an input map provided locally from the file system:

	java pt.ulisboa.tecnico.cnv.solver.SolverMain -d -s GRID_SCAN -w 512 -h 512 -i 'datasets/SIMPLE_VORONOI_512x512_1.png' -o $HOME -yS 25 -xS 25 -x0 0 -x1 50 -y0 0 -y1 50
	
This example starts the radar scanner (-d, printing additional debug information) with a GRID_SCAN strategy (-s) for a map of width 512 (-w) and height 512 (-h) for the map file datasets/SIMPLE_VORONOI_512x512_2021-03-12_21-02-33.png (-i), to be stored in the user's home directory (-o). The scan starts at position x = 25 (-xS) and y = 25 (-yS).

Its argument parser is self-explanatory and a description of its arguments will be printed if one is missing.

	usage: utility-name
	 -d,--debug                    set debug mode.
	 -f,--fill                     should solution path be drawn with dashes?
	 -g,--gradient <arg>           output image's color gradient.
	 -h,--height <arg>             generated image height (default 512
								   pixels).
	 -i,--input <arg>              path to input gradient image to solve.
	 -o,--output-directory <arg>   output directory for generated images.
	 -s,--strategy <arg>           solver strategy can be one of: BFS, DFS or ASTAR.
	 -w,--width <arg>              generated image width (default 512 pixels).
	 -x0 <arg>                     upper-left x coordinate (default 0).
	 -x1 <arg>                     lower-right x coordinate (default equal to
								   image width).
	 -xS <arg>                     starting x coordinate (default 0).
	 -y0 <arg>                     upper-left y coordinate (default 0).
	 -y1 <arg>                     lower-right y coordinate (default equal to
								   image height).
	 -yS <arg>                     starting y coordinate (default 0).

When the solver finishes, it stores an image to disk with an appended timestamp to avoid collisions for the same datasets.

If you want to test locally, you can access existing images at:
http://groups.tecnico.ulisboa.pt/meic-cnv/cnv-project/datasets

### Server - pt.ulisboa.tecnico.cnv.server.WebServer

A simple HTTP server receiving requests. Is is to be run on your Amazon EC2 instances.
The server will parse the **XMLHttpRequest** parameters and pass them to the solver's argument parser.
After the solver checks the parameter values and stores them, the radar scanning strategy (GRID_SCAN/PROGRESSIVE_SCAN/GREEDY_RANGE_SCAN) is executed to map the terrain.

When the solver finishes, the resulting map (with a drawn path) is saved on the temporary directory of the Amazon EC2 instance that has the web server that received the request.
This image is then sent as the response (to the computer where your browser is open) and drawn via JavaScript on the browser.

The following may be performed to run the compiled server class (provided by the faculty).
This launches the example server listening on port 8000:

	cd $HOME/cnv-project
	java pt.ulisboa.tecnico.cnv.server.WebServer

Its argument parser is self-explanatory and a description of its arguments will be printed if one is missing.

    usage: utility-name
     -address <arg>                server listen address (default: 127.0.0.1).
     -d,--debug                    set debug mode.
     -maps <arg>                   directory with maps on the server (default:
                                   WORKING_DIR/datasets).
     -o,--output-directory <arg>   output directory for generated images. By
                                   omission it is the system's temp directory.
     -port <arg>                   server listen port (default: 8000).


The following may be performed to run the compiled server class (provided by the faculty) without Maven:

	cd $HOME/ccv-gradients
	java pt.ulisboa.tecnico.cnv.server.WebServer

Without passing arguments, the **WebServer** will look for a **datasets/** directory in the current working directory.
