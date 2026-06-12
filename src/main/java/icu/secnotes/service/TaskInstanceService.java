package icu.secnotes.service;

import icu.secnotes.pojo.DrillTaskInstance;
import java.util.List;

public interface TaskInstanceService {

    DrillTaskInstance startInstance(Integer taskId, Integer userId);

    DrillTaskInstance getInstance(Integer instanceId);

    DrillTaskInstance getInstanceByTaskAndUser(Integer taskId, Integer userId);

    List<DrillTaskInstance> getInstancesByTask(Integer taskId);

    List<DrillTaskInstance> getInstancesByUser(Integer userId);

    void completeInstance(Integer instanceId);

    void expireInstance(Integer instanceId);
}
