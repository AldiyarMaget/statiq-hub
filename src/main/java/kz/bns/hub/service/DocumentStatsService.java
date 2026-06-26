package kz.bns.hub.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class DocumentStatsService {

    private final AtomicInteger documentCount = new AtomicInteger(0);
    private final AtomicInteger segmentCount  = new AtomicInteger(0);
    // true только после полной индексации И прогрева эмбеддинг-модели.
    private final AtomicBoolean indexed       = new AtomicBoolean(false);

    public void setDocumentCount(int count) { documentCount.set(count); }
    public void setSegmentCount(int count)  { segmentCount.set(count); }
    public int  getDocumentCount()          { return documentCount.get(); }
    public int  getSegmentCount()           { return segmentCount.get(); }

    public void    setIndexed(boolean v)    { indexed.set(v); }
    public boolean isIndexed()              { return indexed.get(); }
}