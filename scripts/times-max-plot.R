#!/usr/bin/Rscript
#

FILE_GROUP_SPLIT <- ' '
FILE_PAR_REGEX <- '^par(\\d+)_.*'
PERCENTILE_THRES <- 0.005
args <- commandArgs(TRUE)


filter_valid_times <- function (x) x >= 0
select_valid_times <- function (lst) {
  return(lst[sapply(lst, filter_valid_times)])
}

select_percentile <- function (lst, pc) {
  pc.low = quantile(lst, pc)
  pc.high = quantile(lst, 1 - pc)
  return(lst[sapply(lst, function (x) x > pc.low && x < pc.high)])
}



group_id <- 0
res_data <- list()
res_par <- list()
for (file_group in args) {
  group_id <- group_id + 1
  cat('processing group:', group_id, '\n')
  file_group_members <- strsplit(file_group, FILE_GROUP_SPLIT)[[1]]

  group_par <- as.numeric(gsub(FILE_PAR_REGEX, '\\1', file_group_members[[1]]))
  accu_sysinit_times <- c()
  accu_wl_times <- c()
  for (fil in file_group_members) {
    cat(' ', fil, '\n')
    data <- read.csv(fil)
    accu_sysinit_times <- append(accu_sysinit_times,
                                 select_percentile(select_valid_times(data$SysInit),
                                                   PERCENTILE_THRES))
    accu_wl_times <- append(accu_wl_times,
                            select_percentile(select_valid_times(data$Workload),
                                              PERCENTILE_THRES))
  }
  max_sysinit_time <- max(accu_sysinit_times)
  max_wl_time <- max(accu_wl_times)
  min_sysinit_time <- min(accu_sysinit_times)
  min_wl_time <- min(accu_wl_times)
  cat('  >>     par:', group_par, '\n')
  cat('  >> sysinit:', min_sysinit_time,'-', max_sysinit_time, '\n')
  cat('  >>      wl:', min_wl_time, '-', max_wl_time,'\n')

  cat(sprintf('\nSI-Mean: %.2f\n', mean(accu_sysinit_times)))
  cat(sprintf('WL-Mean: %.2f\n\n', mean(accu_wl_times)))

  grp_data <- list()
  grp_data$max_sysinit <- max_sysinit_time
  grp_data$min_sysinit <- min_sysinit_time
  grp_data$max_wl <- max_wl_time
  grp_data$min_wl <- min_wl_time
  res_data[[group_par]] <- grp_data
  res_par <- append(res_par, group_par)
}

res_x <- sort(unlist(res_par))

res_sysinit_min <- list()
res_sysinit_max <- list()
res_wl_min <- list()
res_wl_max <- list()
for (par_id in res_x) {
  grp <- res_data[[par_id]]
  res_sysinit_min <- append(res_sysinit_min, grp$min_sysinit)
  res_sysinit_max <- append(res_sysinit_max, grp$max_sysinit)
  res_wl_min <- append(res_wl_min, grp$min_wl)
  res_wl_max <- append(res_wl_max, grp$max_wl)
}
# milliseconds to seconds
res_y_sysinit_min <- unlist(res_sysinit_min) / 1000
res_y_sysinit_max <- unlist(res_sysinit_max) / 1000
res_y_wl_min <- unlist(res_wl_min) / 1000
res_y_wl_max <- unlist(res_wl_max) / 1000

cat(sprintf('Total sysinit min: %.2f sec\n', min(res_y_sysinit_min)))
cat(sprintf('Total sysinit max: %.2f sec\n', max(res_y_sysinit_max)))

cat(sprintf('Total Workload min: %.2f sec\n', min(res_y_wl_min)))
cat(sprintf('Total workload max: %.2f sec\n', max(res_y_wl_max)))

pdf('times-minmax-plot.pdf')
pscale <- 1.5
plot(c(min(res_x), max(res_x)),
     c(min(min(res_y_sysinit_min), min(res_y_wl_min)),
       max(max(res_y_sysinit_max), max(res_y_wl_max))),
     type='n', xlab='Parallel instances', ylab='Time (s)',
#     main='Min/Max Sysinit and Workload Times Observed',
  cex.lab=pscale, cex.axis=pscale, cex.main=pscale)


col.l.si <- rgb(1.0, 0.5, 0.0, 1.0)
col.p.si <- rgb(1.0, 0.5, 0.2, 0.3)
col.l.wl <- rgb(0.0, 0.0, 1.0, 1.0)
col.p.wl <- rgb(0.3, 0.3, 1.0, 0.25)

polygon(c(res_x, rev(res_x)), c(res_y_sysinit_max, rev(res_y_sysinit_min)),
             col=col.p.si, border = NA)
polygon(c(res_x, rev(res_x)), c(res_y_wl_max, rev(res_y_wl_min)),
             col=col.p.wl, border = NA)

lscale <- 1.7
lines(res_x, res_y_sysinit_max, col=col.l.si, lwd=3, type='o', pch=16, cex=lscale)
lines(res_x, res_y_sysinit_min, col=col.l.si, lwd=3, type='o', pch=15, cex=lscale)
lines(res_x, res_y_wl_max, col=col.l.wl, lwd=3, type='o', pch=16, cex=lscale)
lines(res_x, res_y_wl_min, col=col.l.wl, lwd=3, type='o', pch=15, cex=lscale)

 legend(4, 820, c('Sysinit max','Sysinitn min', 'Workload max', 'Workload min'),
         lty=c(1, 1, 1, 1), lwd=c(3, 3, 3, 3),
         col=c(col.l.si, col.l.si, col.l.wl, col.l.wl),
         pch=c(16, 15, 16, 15), cex=pscale)

dev.off()



