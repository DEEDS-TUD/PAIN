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


  lines(c(f.med, f.med), f.y, lwd=4, col=rgb(0,0,1,0.4))
  lines(c(f.q1, f.q1), f.y, lwd=4, col=rgb(0,0,1,0.4))
  lines(c(f.q2, f.q2), f.y, lwd=4, col=rgb(0,0,1,0.4))
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

  # get rid of invalid entries
  acc.si <- select_valid_times(accu_sysinit_times)
  acc.wl <- select_valid_times(accu_wl_times)

  acc.si.n <- length(acc.si)
  acc.wl.n <- length(acc.wl)

  si_times <- c()
  wl_times <- c()
  sample_sizes.pc <- seq(0.1, 1, 0.1) #c(0.25, 0.5, 0.75, 1.0)
  sample_sizes.si <- c()
  sample_sizes.wl <- c()

  for (cur_pc in sample_sizes.pc) {
    cat('\n')
    cur.si.n <- round(acc.si.n * cur_pc)
    cur.wl.n <- round(acc.wl.n * cur_pc)
    cur.si <- sample(acc.si, cur.si.n)
    cur.wl <- sample(acc.wl, cur.wl.n)

    cat(sprintf('  Level        : %.2f\n', cur_pc))
    cat(sprintf('  SysInitsize  : %d\n', cur.si.n))
    cat(sprintf('  Workload size: %d\n', cur.wl.n))

    cur.si.mean <- mean(cur.si)
    cur.si.sd <- sd(cur.si)
    cur.wl.mean <- mean(cur.wl)
    cur.wl.sd <- sd(cur.wl)

    cat(sprintf('  SysInit mean : %.2f\n', cur.si.mean))
    cat(sprintf('  SysInit sd   : %.2f\n', cur.si.sd))
    cat(sprintf('  Workload mean: %.2f\n', cur.wl.mean))
    cat(sprintf('  Workload sd  : %.2f\n', cur.wl.sd))

    cur.si.est <- qnorm(0.9999, cur.si.mean, cur.si.sd)
    cur.wl.est <- qnorm(0.9999, cur.wl.mean, cur.wl.sd)

    cat(sprintf('  Est SysInit : %.2f\n', cur.si.est))
    cat(sprintf('  Est Workload: %.2f\n', cur.wl.est))

    sample_sizes.si <- append(sample_sizes.si, cur.si.n)
    sample_sizes.wl <- append(sample_sizes.wl, cur.wl.n)
    si_times <- append(si_times, cur.si.est)
    wl_times <- append(wl_times, cur.wl.est)
  }

  si_times <- si_times / 1000
  wl_times <- wl_times / 1000

  col.l.si <- rgb(1.0, 0.5, 0.0, 1.0)
  col.l.wl <- rgb(0.0, 0.0, 1.0, 1.0)

  pscale <- 1.5
  lscale <- 1.7
  pdf('est-timeouts.pdf')
  plot(c(min(sample_sizes.pc), max(sample_sizes.pc)),
       c(min(wl_times, si_times), max(wl_times, si_times)),
       type='n', ylab='Estimated timeout values (s)',
       xlab='Sample size (% of available samples)',
       cex.lab=pscale, cex.axis=pscale, cex.main=pscale)
  lines(sample_sizes.pc, si_times, col=col.l.si, lwd=3, type='o', pch=16, cex=lscale)
  lines(sample_sizes.pc, wl_times, col=col.l.wl, lwd=3, type='o', pch=16, cex=lscale)

  legend(0.7, 650, c('Sysinit', 'Workload'),
         lty=c(1,1), lwd=c(3, 3), col=c(col.l.si, col.l.wl),
         pch=c(16, 16), cex=pscale)
  dev.off()

  pdf('est-timeouts-si.pdf')
  plot(c(min(sample_sizes.si), max(sample_sizes.si)),
       c(min(si_times), max(si_times)),
       type='n', ylab='Estimated timeout values (s)',
       xlab='Sample size',
       cex.lab=pscale, cex.axis=pscale, cex.main=pscale)
  lines(sample_sizes.si, si_times, col=col.l.si, lwd=3, type='o', pch=16, cex=lscale)
  dev.off()

  pdf('est-timeouts-wl.pdf')
  plot(c(min(sample_sizes.wl), max(sample_sizes.wl)),
       c(min(wl_times), max(wl_times)),
       type='n', ylab='Estimated timeout values (s)',
       xlab='Sample size',
       cex.lab=pscale, cex.axis=pscale, cex.main=pscale)
  lines(sample_sizes.wl, wl_times, col=col.l.wl, lwd=3, type='o', pch=16, cex=lscale)
  dev.off()

  df.si <- cbind(sample_sizes.si, si_times)
  colnames(df.si) <- c('sample-size', 'est-si-timeout')
  df.wl <- cbind(sample_sizes.wl, wl_times)
  colnames(df.wl) <- c('sample-size', 'est-si-timeout')
  write.csv(df.si, 'est-timesouts-si.csv', row.names=F)
  write.csv(df.wl, 'est-timesouts-wl.csv', row.names=F)



#  max_sysinit_time <- max(accu_sysinit_times)
#  max_wl_time <- max(accu_wl_times)
#  min_sysinit_time <- min(accu_sysinit_times)
#  min_wl_time <- min(accu_wl_times)
#  cat('  >>     par:', group_par, '\n')
#  cat('  >> sysinit:', min_sysinit_time,'-', max_sysinit_time, '\n')
#  cat('  >>      wl:', min_wl_time, '-', max_wl_time,'\n')

#  est_sysinit <- qnorm(.9999, mean(accu_sysinit_times), sd(accu_sysinit_times))
#  est_wl <- qnorm(0.999, mean(accu_wl_times), sd(accu_wl_times))
#  cat('  >> est sysinit:', est_sysinit, '\n')
#  cat('  >> est Workload:', est_wl, '\n')

  
#  print(mean(accu_wl_times))
#  print(sd(accu_wl_times))
}



