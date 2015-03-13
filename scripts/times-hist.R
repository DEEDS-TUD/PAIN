#!/usr/bin/Rscript
#

args <- commandArgs(TRUE)
bin_stepsize <- 10000 / 1000

print_hist <- function (lst, filter, step, name, xlab='times [ms]', sample_count=TRUE) {
  filtered = lst[sapply(lst, filter)]
  min_data = step * floor(min(filtered) / step)
  bins_data = seq(min_data, max(filtered) + step, step)
  title = if (sample_count)
    paste(name, '\n', length(filtered), '/', length(lst), sep='')
    else name

  hist(filtered, breaks=bins_data, main=title, xlab=xlab)
}

filter_valid_times <- function (x) x >= 0

for (fil in args) {
  cat('Processing file:', fil, '\n')
  name <- sub('\\..*$', '', fil)

  data <- read.csv(fil)

  pdf(paste(name, '-hist.pdf', sep=''))
  print_hist(data$SysInit / 1000, filter_valid_times, bin_stepsize, name,
             'sys init time [sec]')
  print_hist(data$Workload / 1000, filter_valid_times, bin_stepsize, name,
             'wl time [sec]')

#  hist(sysinit_times, breaks=bins, main=name)
  dev.off()

}


