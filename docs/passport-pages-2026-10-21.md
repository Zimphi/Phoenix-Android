# Passport inner page

The ePassport document preview flips on touch between the existing landscape
photo/MRZ page and a portrait supplementary page. Both fit the same preview frame,
so tabs and data-group controls do not jump. The phone is not forced to rotate.

The portrait template is the approved Phoenix mockup, with the remaining eagle
background removed. Its fixed labels, rules, page number and watermark are retained.
The image asset was produced with built-in Imagegen by removing only the variable
example values from the approved mockup; real values are drawn at those coordinates.
It is stored in `app/src/main/res/drawable-nodpi/passport_details_template.png`.
CAN comes from the selected profile; residence and title are read from standardized
DG11 text using the existing shared BER parser. Missing values use an em dash.
Height, eye colour and pseudonym have no mapped source in the current passport
profiles and are not populated with the mockup's fictional example values.
The protocol cores, profile files and signed data are unchanged by this display.

Validation on SM-S918B: Android build and lint passed; 29 adapter unit tests passed;
three TesterArmy device tests passed. Screenshots were inspected. Tests check the
portrait aspect ratio, unchanged frame/control bounds over repeated flips, CAN
binding, catalogue profiles and the existing eID front/back view.
