#!/bin/bash


./times-max-plot.R \
  "$(echo par4_mixed_deeds-times*.csv)" \
  "$(echo par5_mixed_deeds-times*.csv)" \
  "$(echo par6_mixed_deeds-times*.csv)" \
  "$(echo par7_mixed_deeds-times*.csv)" \
  "$(echo par8_mixed_deeds-times*.csv)" \
  "$(echo par10_mixed_deeds-times*.csv)"

# too many hangs assumed, timeout was not high enough
#  "$(echo par12_mixed_deeds-times*.csv)" \
#  "$(echo par14_mixed_deeds-times*.csv)"



