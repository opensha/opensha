! This program is an example which illustrates how to read
! earthquake simulator input files.


program input_example

implicit none

integer :: section
integer :: vertex
integer :: element


! Container parameters

integer, parameter :: ctr_recl = 2504   ! Maximum record length

! Geometry summary record

integer, parameter :: gsum_kind = 200   ! Kind for geometry file, summary record
integer :: gsum_n_section               ! Total number of fault sections
integer :: gsum_n_vertex                ! Total number of vertices
integer :: gsum_n_triangle              ! Total number of triangles
integer :: gsum_n_rectangle             ! Total number of rectangles
real(8) :: gsum_lat_lo                  ! Lowest latitude
real(8) :: gsum_lat_hi                  ! Highest latitude
real(8) :: gsum_lon_lo                  ! Lowest longitude
real(8) :: gsum_lon_hi                  ! Highest longitude
real(8) :: gsum_depth_lo                ! Lowest depth
real(8) :: gsum_depth_hi                ! Highest depth

! Geometry section record

integer, parameter :: gsec_kind = 201   ! Kind for geometry file, section information record
integer :: gsec_sid                     ! Section identification number
character(100) :: gsec_name             ! Section name
integer :: gsec_n_vertex                ! Number of vertices within section
integer :: gsec_n_triangle              ! Number of triangles within section
integer :: gsec_n_rectangle             ! Number of rectangles within section
real(8) :: gsec_lat_lo                  ! Lowest latitude within section
real(8) :: gsec_lat_hi                  ! Highest latitude within section
real(8) :: gsec_lon_lo                  ! Lowest longitude within section
real(8) :: gsec_lon_hi                  ! Highest longitude within section
real(8) :: gsec_depth_lo                ! Lowest depth within section
real(8) :: gsec_depth_hi                ! Highest depth within section
real(8) :: gsec_das_lo                  ! Lowest distance along strike within section
real(8) :: gsec_das_hi                  ! Highest distance along strike within section
integer :: gsec_fault_id                ! Fault identification number

! Geometry vertex record

integer, parameter :: gver_kind = 202   ! Kind for geometry file, vertex record
integer :: gver_index                   ! Vertex index number (counts beginning with 1)
real(8) :: gver_lat                     ! Latitude (degrees, positive north)
real(8) :: gver_lon                     ! Longitude (degrees, positive east)
real(8) :: gver_depth                   ! Depth (meters, negative underground)
real(8) :: gver_das                     ! Distance along strike (meters)
integer :: gver_trace_flag              ! On-trace flag (0=not on trace, 1=on trace, 2=initial point of trace, 3=final point of trace)

! Geometry triangle record

integer, parameter :: gtri_kind = 203   ! Kind for geometry file, triangle record
integer :: gtri_index                   ! Element index number (counts beginning with 1)
integer :: gtri_vertex_1                ! Index number for vertex #1
integer :: gtri_vertex_2                ! Index number for vertex #2
integer :: gtri_vertex_3                ! Index number for vertex #3
real(8) :: gtri_rake                    ! Rake angle (degrees)
real(8) :: gtri_slip_rate               ! Slip rate (meters/second)
real(8) :: gtri_aseis_factor            ! Aseismicity factor
real(8) :: gtri_strike                  ! Strike angle (degrees)
real(8) :: gtri_dip                     ! Dip angle (degrees)

! Geometry rectangle record

integer, parameter :: grec_kind = 204   ! Kind for geometry file, rectangle record
integer :: grec_index                   ! Element index number (counts beginning with 1)
integer :: grec_vertex_1                ! Index number for vertex #1
integer :: grec_vertex_2                ! Index number for vertex #2
integer :: grec_vertex_3                ! Index number for vertex #3
integer :: grec_vertex_4                ! Index number for vertex #4
real(8) :: grec_rake                    ! Rake angle (degrees)
real(8) :: grec_slip_rate               ! Slip rate (meters/second)
real(8) :: grec_aseis_factor            ! Aseismicity factor
real(8) :: grec_strike                  ! Strike angle (degrees)
real(8) :: grec_dip                     ! Dip angle (degrees)
integer :: grec_perfect_flag            ! Perfect rectangle flag (0=not perfect, 1=perfect)

! Friction summary record

integer, parameter :: fsum_kind = 200   ! Kind for friction file, summary record
integer :: fsum_n_element               ! Total number of elements
integer :: fsum_elastic_flag            ! Elastic parameters flag (1=included, 0=not included)
integer :: fsum_strength_flag           ! Fault strength flag (1=included, 0=not included)
integer :: fsum_rate_state_flag         ! Rate-state initial state flag (1=included, 0=not included)

! Friction elastic parameter record

integer, parameter :: felp_kind = 201   ! Kind for friction file, elastic parameter record
real(8) :: felp_lame_lambda             ! Lame modulus lambda (Pascal)
real(8) :: felp_lame_mu                 ! Lame modulus mu, also known as the shear modulus (Pascal)

! Friction fault strength record

integer, parameter :: fstr_kind = 202   ! Kind for friction file, fault strength record
integer :: fstr_index                   ! Element index number
real(8) :: fstr_static_strength         ! Static yield strength (Pascal)
real(8) :: fstr_dynamic_strength        ! Dynamic sliding strength (Pascal)

! Friction rate-state parameter record

integer, parameter :: frsp_kind = 203   ! Kind for friction file, rate-state parameter record
integer :: frsp_index                   ! Element index number
real(8) :: frsp_A                       ! Rate-state parameter A
real(8) :: frsp_B                       ! Rate-state parameter B
real(8) :: frsp_L                       ! Rate-state critical distance L (meters)
real(8) :: frsp_f0                      ! Rate-state friction coefficient
real(8) :: frsp_V0                      ! Rate-state reference velocity (meters/second)

! Initial condition summary record

integer, parameter :: csum_kind = 200   ! Kind for initial condition file, summary record
integer :: csum_n_element               ! Total number of elements
integer :: csum_stress_flag             ! Initial stress flag (1=included, 0=not included)
integer :: csum_state_flag              ! Initial state flag (1=included, 0=not included)

! Initial condition stress record

integer, parameter :: csts_kind = 201   ! Kind for initial condition file, initial stress record
integer :: csts_index                   ! Element index number
real(8) :: csts_shear_stress            ! Initial shear stress (Pascal)
real(8) :: csts_normal_stress           ! Initial normal stress (Pascal)

! Initial condition state record

integer, parameter :: crst_kind = 202   ! Kind for initial condition file, initial state record
integer :: crst_index                   ! Element index number
real(8) :: crst_rs_theta                ! Initial rate-state variable theta (seconds)


! In this simple example we will just write out a summary of the input.
! A real simulator would use the input to build up a data structure in memory.
! This is our output file.

open(10, file='input_example_result.dat', status='replace', recl=ctr_recl)

! Open geometry file, and check the signature.
! Note we use spec level 2, for file format version 0.4.

open(7, file='NCA_Ward_Geometry.dat', status='old', recl=ctr_recl)
call check_signature (7, 'EQSim_Input_Geometry_2', 2)

! Read the fault geometry summary record

call find_record (7, gsum_kind)
read(7,*) gsum_n_section, gsum_n_vertex, gsum_n_triangle, gsum_n_rectangle,    &
          gsum_lat_lo, gsum_lat_hi, gsum_lon_lo, gsum_lon_hi, gsum_depth_lo, gsum_depth_hi
          
if (gsum_n_triangle .ne. 0) then
    write(6,*) 'Geometry file contains triangles, but we only support rectangles in this example.'
    stop
endif

write(10,*) 'Geometry summary, sections=', gsum_n_section, ', vertices=', gsum_n_vertex, ', rectangles=', gsum_n_rectangle

! Open friction file, and check the signature

open(8, file='NCA_Ward_Friction.dat', status='old', recl=ctr_recl)
call check_signature (8, 'EQSim_Input_Friction_2', 1)

! Read the friction summary record

call find_record (8, fsum_kind)
read(8,*) fsum_n_element, fsum_elastic_flag, fsum_strength_flag, fsum_rate_state_flag
          
if (fsum_elastic_flag .eq. 0) then
    write(6,*) 'Friction file does not contain elastic parameters, but we require it in this example.'
    stop
endif
          
if (fsum_strength_flag .eq. 0) then
    write(6,*) 'Friction file does not contain fault strangth, but we require it in this example.'
    stop
endif

if (fsum_n_element .ne. gsum_n_triangle + gsum_n_rectangle) then
    write(6,*) 'Friction file element count does not match geometry file.'
    stop
endif

write(10,*) 'Friction summary, elements=', fsum_n_element
        
! Read the elastic parameter record from the friction file

call find_record (8, felp_kind)
read(8,*) felp_lame_lambda, felp_lame_mu

write(10,*) 'Elastic parameters, lame lambda=', felp_lame_lambda, 'lame mu=', felp_lame_mu

! Open initial condition file, and check the signature

open(9, file='NCA_Ward_Condition.dat', status='old', recl=ctr_recl)
call check_signature (9, 'EQSim_Input_Condition_2', 1)

! Read the initial condition summary record

call find_record (9, csum_kind)
read(9,*) csum_n_element, csum_stress_flag, csum_state_flag
         
if (csum_stress_flag .eq. 0) then
    write(6,*) 'Initial condition file does not contain stress, but we require it in this example.'
    stop
endif
 
if (csum_n_element .ne. gsum_n_triangle + gsum_n_rectangle) then
    write(6,*) 'Initial condition file element count does not match geometry file.'
    stop
endif

write(10,*) 'Initial condition summary, elements=', fsum_n_element

! Loop over all fault sections

do section = 1,gsum_n_section

    ! Read the section information record from the geometry file
    
    call find_record (7, gsec_kind)
    read(7,*) gsec_sid, gsec_name, gsec_n_vertex, gsec_n_triangle, gsec_n_rectangle,    &
              gsec_lat_lo, gsec_lat_hi, gsec_lon_lo, gsec_lon_hi,                       &
              gsec_depth_lo, gsec_depth_hi, gsec_das_lo, gsec_das_hi, gsec_fault_id

    write(10,*) 'Fault section, id=', gsec_sid, ', name=', gsec_name(1:len_trim(gsec_name)),    &
                ', vertices=', gsec_n_vertex, ', rectangles=', gsec_n_rectangle
                
    ! Loop over all vertices within the section
    
    do vertex = 1,gsec_n_vertex
    
        ! Read the vertex record from the geometry file
        
        call find_record (7, gver_kind)
        read(7,*) gver_index, gver_lat, gver_lon, gver_depth, gver_das, gver_trace_flag
        
        write(10,*) 'Vertex, index=', gver_index, ', depth=', gver_depth, ', das=', gver_das
    
    enddo
    
    ! Loop over all elements within the section
    
    do element = 1,gsec_n_rectangle
    
        ! Read the element record from the geometry file, which is a rectangle because we checked above there are no triangles
        
        call find_record (7, grec_kind)
        read(7,*) grec_index, grec_vertex_1, grec_vertex_2, grec_vertex_3, grec_vertex_4,   &
                  grec_rake, grec_slip_rate, grec_aseis_factor,                             &
                  grec_strike, grec_dip, grec_perfect_flag
        
        ! Read the fault strength record from the friction file
        
        call find_record (8, fstr_kind)
        read(8,*) fstr_index, fstr_static_strength, fstr_dynamic_strength
        
        ! Read the initial stress record from the initial condition file
        
        call find_record (9, csts_kind)
        read(9,*) csts_index, csts_shear_stress, csts_normal_stress
        
        write(10,*) 'Rectangle, index=', grec_index, ', rake=', grec_rake,          &
                    ', strike=', grec_strike,                                       &
                    ', dip=', grec_dip,                                             &
                    ', slip rate=', grec_slip_rate,                                 &
                    ', strength=', fstr_static_strength - fstr_dynamic_strength,    &
                    ', shear stress=', csts_shear_stress
    
    enddo

enddo

stop
end program input_example




! This subroutine scans a file until it finds a record
! of the desired kind.  If end-of-file is encountered,
! write an error message and terminate the program.

subroutine find_record (file_unit, record_kind)

implicit none
integer :: file_unit                    ! Fortran unit number
integer :: record_kind                  ! Kind of record to search for

integer :: current_kind                 ! Kind of the current line in the file
integer, parameter :: eof_kind = 999    ! Kind for end-of-file record

do
    read(file_unit,'(i3)',advance='no') current_kind    ! Non-advancing I/O reads just the 3-digit record kind
    
    if (current_kind .eq. record_kind) exit     ! Found our kind of record, stop searching
    
    if (current_kind .eq. eof_kind) then        ! Found end-of-file, it's an error
        write(6,*) 'Found end-of-file when searching for record kind=', record_kind, ', in unit=', file_unit
        stop
    endif
    
    read(file_unit,*)           ! Skip this record
enddo

return
end subroutine find_record




! This subroutine reads the first record of a file, and
! checks that it is a signature record with the desired
! signature keyword and specification level.

subroutine check_signature (file_unit, desired_keyword, desired_spec_level)

implicit none
integer :: file_unit                ! Fortran unit number
character(*) :: desired_keyword     ! Expected signature keyword
integer :: desired_spec_level       ! Expected specification level

integer :: current_kind             ! Kind of the current line in the file

! Signature record

integer, parameter :: sig_kind = 101
character(100) :: sig_keyword
integer :: sig_spec_level

! Read the kind of the first record, and check it

read(file_unit,'(i3)',advance='no') current_kind    ! Non-advancing I/O reads just the 3-digit record kind

if (current_kind .ne. sig_kind) then
    write(6,*) 'First line of file is not a signature record, in unit=', file_unit
    stop
endif

! Read the contents of the signature record

read(file_unit,*) sig_keyword, sig_spec_level

! Check signature keyword

if (sig_keyword .ne. desired_keyword) then
    write(6,*) 'Incorrect file signature, in unit=', file_unit
    stop
endif

! Check specification level

if (sig_spec_level < desired_spec_level) then
    write(6,*) 'Incompatible file specification level, in unit=', file_unit
    stop
endif

return
end subroutine check_signature


