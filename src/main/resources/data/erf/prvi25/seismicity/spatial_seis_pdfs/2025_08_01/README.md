This is Andrea`s v9 spatial PDFs, sent via Teams on 8/1/2025. The files were renamed to match the seismicity region naming structure:

apdf_pmmx_car_interface -> CAR_INTERFACE
`unzip//apdf_pmmx_car_interface_gk_fixed.csv` -> `CAR_INTERFACE/GK_FIXED.csv`
`unzip//apdf_pmmx_car_interface_gk_adN8.csv` -> `CAR_INTERFACE/GK_ADAPTIVE.csv`
`unzip//apdf_pmmx_car_interface_nn_fixed.csv` -> `CAR_INTERFACE/NN_FIXED.csv`
`unzip//apdf_pmmx_car_interface_nn_adN8.csv` -> `CAR_INTERFACE/NN_ADAPTIVE.csv`
`unzip//apdf_pmmx_car_interface_r85_fixed.csv` -> `CAR_INTERFACE/REAS_FIXED.csv`
`unzip//apdf_pmmx_car_interface_r85_adN10.csv` -> `CAR_INTERFACE/REAS_ADAPTIVE.csv`
apdf_pmmx_car_intraslab -> CAR_INTRASLAB
`unzip//apdf_pmmx_car_intraslab_gk_fixed.csv` -> `CAR_INTRASLAB/GK_FIXED.csv`
`unzip//apdf_pmmx_car_intraslab_gk_adN5.csv` -> `CAR_INTRASLAB/GK_ADAPTIVE.csv`
`unzip//apdf_pmmx_car_intraslab_nn_fixed.csv` -> `CAR_INTRASLAB/NN_FIXED.csv`
`unzip//apdf_pmmx_car_intraslab_nn_adN3.csv` -> `CAR_INTRASLAB/NN_ADAPTIVE.csv`
`unzip//apdf_pmmx_car_intraslab_r85_fixed.csv` -> `CAR_INTRASLAB/REAS_FIXED.csv`
`unzip//apdf_pmmx_car_intraslab_r85_adN10.csv` -> `CAR_INTRASLAB/REAS_ADAPTIVE.csv`
apdf_pmmx_crustal -> CRUSTAL
`unzip//apdf_pmmx_crustal_gk_fixed.csv` -> `CRUSTAL/GK_FIXED.csv`
`unzip//apdf_pmmx_crustal_gk_adN3.csv` -> `CRUSTAL/GK_ADAPTIVE.csv`
`unzip//apdf_pmmx_crustal_nn_fixed.csv` -> `CRUSTAL/NN_FIXED.csv`
`unzip//apdf_pmmx_crustal_nn_adN10.csv` -> `CRUSTAL/NN_ADAPTIVE.csv`
`unzip//apdf_pmmx_crustal_r85_fixed.csv` -> `CRUSTAL/REAS_FIXED.csv`
`unzip//apdf_pmmx_crustal_r85_adN2.csv` -> `CRUSTAL/REAS_ADAPTIVE.csv`
apdf_pmmx_mue_interface -> MUE_INTERFACE
`unzip//apdf_pmmx_mue_interface_gk_fixed.csv` -> `MUE_INTERFACE/GK_FIXED.csv`
`unzip//apdf_pmmx_mue_interface_gk_adN10.csv` -> `MUE_INTERFACE/GK_ADAPTIVE.csv`
`unzip//apdf_pmmx_mue_interface_nn_fixed.csv` -> `MUE_INTERFACE/NN_FIXED.csv`
`unzip//apdf_pmmx_mue_interface_nn_adN10.csv` -> `MUE_INTERFACE/NN_ADAPTIVE.csv`
`unzip//apdf_pmmx_mue_interface_r85_fixed.csv` -> `MUE_INTERFACE/REAS_FIXED.csv`
`unzip//apdf_pmmx_mue_interface_r85_adN10.csv` -> `MUE_INTERFACE/REAS_ADAPTIVE.csv`
apdf_pmmx_mue_intraslab -> MUE_INTRASLAB
`unzip//apdf_pmmx_mue_intraslab_gk_fixed.csv` -> `MUE_INTRASLAB/GK_FIXED.csv`
`unzip//apdf_pmmx_mue_intraslab_gk_adN9.csv` -> `MUE_INTRASLAB/GK_ADAPTIVE.csv`
`unzip//apdf_pmmx_mue_intraslab_nn_fixed.csv` -> `MUE_INTRASLAB/NN_FIXED.csv`
`unzip//apdf_pmmx_mue_intraslab_nn_adN6.csv` -> `MUE_INTRASLAB/NN_ADAPTIVE.csv`
`unzip//apdf_pmmx_mue_intraslab_r85_fixed.csv` -> `MUE_INTRASLAB/REAS_FIXED.csv`
`unzip//apdf_pmmx_mue_intraslab_r85_adN10.csv` -> `MUE_INTRASLAB/REAS_ADAPTIVE.csv`

The interface PDFs were then trimmed (and rescaled) to only include cells where the Slab2 depth is >= 50km, using the v2 depth files (in the `prvi25/seismicity/depths` directory).
