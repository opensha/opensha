ALLCAL Fault Model, Converted to EQ Simulator File Formats Version 0.4
Michael Barall  06/28/2010


The following files are included in this package.


ALLCAL_Ward_Geometry.dat -- Geometry file for Steve Ward's ALLCAL model.  All data
in this file derives from the ALLCAL model, except the aseismicity factor which
is set to zero.  Strike, dip, and rake angles are copied unchanged from the ALLCAL
file.  Vertices are not moved;  where there are gaps between elements, there are
multiple vertices with the same depth & DAS.

ALLCAL_Ward_Friction.dat -- Friction file for Steve Ward's ALLCAL model.  Since the
friction file contains both static and dynamic strength, while the ALLCAL model
has just a single fault strength, I set the dynamic strength to zero.  The
elastic parameters are arbitrary.


