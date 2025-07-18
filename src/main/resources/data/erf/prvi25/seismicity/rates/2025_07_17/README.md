These data incoporate the following updates over the earlier draft model:

* These data use new fixes for default depths, wherein surrogate depths from nearby known-depth events are used to determine average classification probabilities.
* The rate model calculation now randomly selects catalogs using those classification probabilities (prior calculations assigned events fully to the classification with the highest probability).
* Interface Seismicity regions were expanded slightly; prior models had some events slip through the cracks (e.g., assigned interface but not in the interface region).

We now also track the epochs separately. The `1900_2023` subdirectory contains results for the full catalog (both epochs), and are Andy's 'v9' files sent via Teams on 7/17/2025. The `1973_2023` subdirectory contains results for only the more recent epoch, and are Andy's 'v10' files sent at the same time.
