#!/usr/bin/Rscript
#

library(RMySQL)

FAIL_NAMES <- list(
  'experiment_failure',
  'none',
  'finished',
  'system_hang_detected',
  'system_hang_assumed',
  'system_crash_detected',
  'application_hang_detected',
  'application_hang_assumed',
  'application_fault_detected',
  'sysinit_hang_assumed',
  'system_oops_detected'
)

FAIL_NUM <- 11
FAILID_REMAP <- function (real_id) real_id + 2
FAILID_SIHANG <- 10
FAILID_WLHANG <- 8

args <- commandArgs(TRUE)
virt_sysinit <- as.numeric(args[[1]])
virt_workload <- as.numeric(args[[2]])
args <- args[3:length(args)]

if (is.na(virt_sysinit) || is.na(virt_workload)) {
  stop('Invalid virtual timeouts specified')
}

cat(sprintf('\nvirtual sysinit: %d ms (%d s)\nvirtual worload: %d ms (%d s)\n\n',
            virt_sysinit, round(virt_sysinit / 1000),
            virt_workload, round(virt_workload / 1000)))

get_db_name <- function (name) {
  run_id = gsub('.*(\\d+)$','\\1', name)
  base_name = gsub('^(.+)-times.*', '\\1', name)
  return(paste(base_name, run_id, sep=''))
}

get_csv_out_file <- function (file_name) {
  base_name = gsub('^(.+)-times.*', '\\1', name)
  return(paste(base_name, '-virtual.csv', sep=''))
}

calc_distrib <- function(data, to.si, to.wl) {
  dist = rep(0, FAIL_NUM)
  do_count <- function (row) {
    if (row$SysInit >= to.si) {
      dist[[FAILID_SIHANG]] <<- dist[[FAILID_SIHANG]] + 1
    } else if (row$Workload >= to.wl) {
        dist[[FAILID_WLHANG]] <<- dist[[FAILID_WLHANG]] + 1
    } else {
      dist[[row$result]] <<- dist[[row$result]] + 1
    }
  }
  by(data, 1:nrow(data), do_count)
  return(dist)
}



cat('Opening database connection...\n')
db_conn <- dbConnect(MySQL(), user='grinder', password='grinder', host="localhost")
on.exit(dbDisconnect(db_conn))

distrib <- list()
csv_out_file <- NA
for (fil in args) {
  cat('Processing file:', fil, '\n')
  name <- sub('\\..*$', '', fil)
  db_name <- get_db_name(name)
  cat('  DB name:', db_name, '\n')

  if (is.na(csv_out_file)) {
    csv_out_file <- get_csv_out_file(name)
  }

  cat('  Reading times from CSV...\n')
  dat_times <- read.csv(fil)

  cat('  Selecting database...\n')
  dbGetQuery(db_conn, sprintf('use %s', db_name))

  cat('  Querying experiment run...\n')
  dat_results <- dbGetQuery(db_conn, 'SELECT testcase_id, result FROM experiment_run')

  merged <- merge(dat_times, dat_results, by.x='Testcase', by.y='testcase_id')
  merged$result <- FAILID_REMAP(merged$result)

  distrib[[db_name]] <- calc_distrib(merged, virt_sysinit, virt_workload)

}

df <- data.frame(matrix(unlist(distrib), nrow=length(distrib), byrow=T))
rownames(df) <- names(distrib)
colnames(df) <- FAIL_NAMES

write.csv(df, csv_out_file)

