#!/usr/bin/Rscript
#

FILE_GROUP_SPLIT <- ' '
FILE_PAR_REGEX <- '^par(\\d+)_.*'
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

  f.med <- median(filtered)
  f.q1 <- quantile(filtered, 0.005)
  f.q2 <- quantile(filtered, 0.995)
  f.y <- c(0, 120)


#  lines(c(f.med, f.med), f.y, lwd=4, col=rgb(0,0,1,0.4))
#  lines(c(f.q1, f.q1), f.y, lwd=4, col=rgb(0,0,1,0.4))
#  lines(c(f.q2, f.q2), f.y, lwd=4, col=rgb(0,0,1,0.4))
}

filter_valid_times <- function (x) x >= 0
select_valid_times <- function (lst) {
  return(lst[sapply(lst, filter_valid_times)])
}



group_id <- 0
res_data <- list()
res_par <- list()
for (file_group in args) {
  group_id <- group_id + 1
  cat('processing group:', group_id, '\n')
  file_group_members <- strsplit(file_group, FILE_GROUP_SPLIT)[[1]]

  group_par <- gsub(FILE_PAR_REGEX, '\\1', file_group_members[[1]])
  accu_sysinit_times <- c()
  accu_wl_times <- c()
  for (fil in file_group_members) {
    cat(' ', fil, '\n')
    data <- read.csv(fil)
    accu_sysinit_times <- append(accu_sysinit_times, data$SysInit)
    accu_wl_times <- append(accu_wl_times, data$Workload)
  }

# from milliseconds to seconds
  accu_sysinit_times <- accu_sysinit_times / 1000
  accu_wl_times <- accu_wl_times / 1000

  pdf(paste('par', group_par, '-hist.pdf', sep=''))
  print_hist(accu_sysinit_times, filter_valid_times, bin_stepsize,
             'Accumulated SysInit Times', 'sys init time [sec]')
  print_hist(accu_wl_times, filter_valid_times, bin_stepsize,
             'Accumulated WL Times', 'wl time [sec]')
  dev.off()



  max_sysinit_time <- max(accu_sysinit_times)
  max_wl_time <- max(accu_wl_times)
  min_sysinit_time <- min(accu_sysinit_times)
  min_wl_time <- min(accu_wl_times)
  cat('  >>     par:', group_par, '\n')
  cat('  >> sysinit:', min_sysinit_time,'-', max_sysinit_time, '\n')
  cat('  >>      wl:', min_wl_time, '-', max_wl_time,'\n')
  
}



