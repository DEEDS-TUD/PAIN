#include "taskutil.h"

#include <linux/sched.h>
#include <linux/list.h>
#include <asm/current.h>
#include <linux/string.h>
#include <linux/slab.h>
#include <linux/mm.h>

#define THRESHOLD_PROCESS_MEMORY 900000 /* byte */
#define THRESHOLD_PROCESS_RUNTIME 5 /* threshold is given in consecutive runs of the heavy detector */

static struct task_struct *task;

struct runtime_data {
    struct task_struct *task;
    struct list_head list;
    int occurence;
};

LIST_HEAD(running_task_list);

/* returns 1 if a process has an abnormal memory consumption */
int check_abnormal_memory_consumption()
{
    int ret = 0;
    long max_size = 0;

    rcu_read_lock();
    for_each_process(task) {
        if (task->mm == NULL)
            continue;

        /* TODO: remove */
        if (PAGE_SIZE * get_mm_rss(task->mm) > max_size) {
            max_size = PAGE_SIZE * get_mm_rss(task->mm);
            printk(KERN_DEBUG
                   "cande: new maxmem found: %ld pid: %d\n",
                   max_size, task->pid);
        }
        /* rss = num of pages consumed in physical memory */
        if (get_mm_rss(task->mm) * PAGE_SIZE >=
                THRESHOLD_PROCESS_MEMORY) {
            ret = 1;
            goto exit;
        }
    }

exit:
    rcu_read_unlock();
    return ret;
}

/* returns 1 if a process runs for an abnormally long time continuously */
int check_abnormal_runtime()
{
    struct runtime_data *new, *tmp;
    struct list_head *curHead, *q;

    printk(KERN_DEBUG "LIST: "); /* TODO: remove */
    /* update list */
    rcu_read_lock();
    list_for_each_safe(curHead, q, &running_task_list) {
        tmp = list_entry(curHead, struct runtime_data, list);
        printk(KERN_DEBUG "Pid: %d Occ: %d |", tmp->task->pid, tmp->occurence); /* TODO: remove */

        /* remove entries if the corresponding task was removed from runqueue */
        /* state 0 = runnable */
        if (tmp->task == NULL || tmp->task->state != 0) {
            list_del(curHead);
            kfree(tmp);
        } else {
            tmp->occurence++;
            if (tmp->occurence == THRESHOLD_PROCESS_RUNTIME)
                return 1;
        }
    }

    printk(KERN_DEBUG "\nRunning:"); /* TODO: remove */
    /* add new running tasks */
    for_each_process(task) {
        /* exclude heavy detector */
        /* exclude adb task - running consecutively all time */
        if (task->state != 0 || current->pid == task->pid
                || strcmp(task->comm, "/sbin/adbd"))
            continue;

        printk(KERN_DEBUG "%d|", task->pid); /* TODO: remove */
        list_for_each_entry(tmp, &running_task_list, list) {
            if (task->pid == tmp->task->pid)
                break; /* task already in list */
        }
        new = kmalloc(sizeof(*new), GFP_KERNEL);
        new->task = task;
        new->occurence = 0;
        list_add(&new->list, &running_task_list);
    }
    rcu_read_unlock();
    return 0;
}
