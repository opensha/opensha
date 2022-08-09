# Geodetic Deformation Models

Geodetic deformation model file history

## 2022/08/05

Kevin noticed that these files don't match minor changes between v1.4 and v2 of the geologic fault model.

Notably, the traces were reversed for faults 2922 (South Granite Mountains) and 1098 (Ruby Mountains). Kevin flipped each trace around in each model to match v2 of the fault model. Slip rates, standard deviations, and reakes are still associated with the same locations, just the order in the file and minisection IDs are modified. This was done with the code at scratch.kevin.nshm23.DefModelTraceFlipper in opensha-dev.

Received permission to make this change from the deformation modelers as follows:

* Pollitz model via e-mail from Fred on 8/2/2022, subject "Re: Changes to a few faults in the final NSHM23 fault model, updates to deformation models needed?"
* Shen-Bird model via e-mail from Zheng-Kang on 8/2/2022, subject "Re: Changes to a few faults in the final NSHM23 fault model, updates to deformation models needed?"
* Evans model, have not received a reply as of 8/5/2022
* Zeng model, have not received a reply as of 8/5/2022

It is also noted that the geologic rake was changed from -150 to 180 for faults 64 and 65, north and south Death Valley.

## 2022/07/23

Received updated Shen-Bird deformation model via e-mail, subject "Re: Updated deformation model files". These files now match v1.4 of the fault model.

Kevin made the following minor changes:

* Corrected Zayante - Vergalez ID to match final fault model (was 305, now 304)
* Swapped longitude and latitude columns to match file format

Kevin also manually corrected the Zayante - Vergalez ID on the Evans deformation model files on this date to match v1.4 of the fault model.

## 2022/07/22

Received updated Zeng deformation model via e-mail, subject "Re: Updated deformation model files". These files now match v1.4 of the fault model.

Kevin made the following minor change:

* Corrected Zayante - Vergalez ID to match final fault model (was 305, now 304)

## 2022/07/18

Received updated Pollitz deformation model via e-mail, subject "Re: WUS Deformation Model Review Meeting", had been missing Zayante - Vergales, and some faults that were mapped to a prior v1.2 of the geologic deformation model, now matches v1.4 of the fault model.

Kevin made the following minor change:

* Corrected Zayante - Vergalez ID to match final fault model (was 305, now 304)

## 2022/07/13

First files received from Fred Pollitz for each deformation model via e-mail, subject "NSHM23 Deformation Model and Creep Data", files dated 6/27/2022, file name "Deformation_Models_On_Fault_Results-27Jun22.tar.gz"
