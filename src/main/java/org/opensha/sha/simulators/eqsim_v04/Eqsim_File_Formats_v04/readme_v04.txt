EQ Simulator File Formats, Version 0.4
Michael Barall  06/28/2010


The following files are included in this package.


Eqsim_Container_v04.doc -- General container format.  The other formats are
all special cases of the container format.

Eqsim_Input_Geometry_v04.doc -- Input geometry format.  The geometry file
contains the list of fault sections, vertices, and elements.  Elements can
be either rectangles or triangles.  It also contains the rake angle, strike
angle, dip angle, slip rate, and aseismicity factor for each element.

Eqsim_Input_Friction_v04.doc -- Input friction format.  The friction file may
contain any or all of:  (a) elastic parameters for the model;  (b) static and
dynamic fault strength for each element;  and (c) rate-state parameters for
each element.

Eqsim_Input_Condition_v04.doc -- Input initial condition format.  For each
element, the initial condition file may contain:  (a) initial shear and
normal stress;  and (b) initial state variable for rate-state friction.

Eqsim_Output_Event_v04.doc -- Output event list format.  The output file
contains a list of earthquakes, and for each earthquake gives magnitude,
time and duration, rupture extent, hypocenter location, area, slip, moment,
and shear and normal stress before and after the event.  Optionally, there
can be a slip map that gives the distribution of slip and stress over the
fault surface.


NCA_Ward_Geometry.dat -- Geometry file for Steve Ward's NCA model.  All data
in this file derives from the NCA model, except the aseismicity factor which
is set to zero.  Vertices are moved slightly to knit the elements into a
continuous mesh with no gaps.  Strike angles are adjusted accordingly.

NCA_Ward_Friction.dat -- Friction file for Steve Ward's NCA model.  Since the
friction file contains both static and dynamic strength, while the NCA model
has just a single fault strength, I set the dynamic strength to zero.  The
elastic parameters are arbitrary.

NCA_Ward_Condition.dat -- Example initial condition file for Steve Ward's NCA
model.  Since the NCA model does not contain initial conditions, I invented
some depth-dependent initial stresses.  Do not use these in any real
calculation.  I include this merely as an example of the file format.

NCA_Ward_Event.dat -- Example output file for Steve Ward's NCA model.  This
is not real simulator output.  I used a random number generator to produce a
list of events.  I include this as an example of the file format.  When you
produce your own output files, you could use the header from this file as the
header for your own files.


input_example.f90 -- Example Fortran code which illustrates how to read the
three input files.

