package xdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;

import xdb.Procedure.Broadcast;
import xdb.Procedure.ExecuteProcedure;
import xdb.Procedure.ExecuteRunnable;
import xdb.Procedure.SendResponse;
import xdb.Procedure.SendToRole;
import xdb.Procedure.SendToRoles;
import xdb.Procedure.SendToXio;
import xdb.Procedure.Task;

public final class Transaction {
	private int trancount = 0;
	private List<Savepoint> logs = new ArrayList<Savepoint>();

	// friend class Logs
	Map<LogKey, Object> wrappers = new HashMap<LogKey, Object>();

	//////////////////////////////////////////////////////////////////////
	// Record ��ģ�壬�Ƚ����ᡣ
	// Ҳ���ð� Record ��¼������ dump ��Ϣ�ḻһЩ��
	// �� Lockey �ҵ� Record ���е㲻���㡣
	// �������������Ƚϼ�ࡣ{�Ż������ػ���Lockey������������lock���ȼ�鱾�أ�û����ȥLockeysȡ}
	private HashMap<Lockey, Lockey> locks = new HashMap<Lockey, Lockey>();
	
	// ��¼�Ķ����ı� { �Ż���Listener��logNotifyʱ��ֻ�����޸Ĺ��ı� }
	private HashMap<String, TTable<?, ?>> logNotifyTTables = new HashMap<String, TTable<?, ?>>();
	
	// ��������cacheס�ù��ļ�¼
	// { �Ż���TRecordʹ�ù��󣬱���cacheס������TTableCache������ͻ }
	// { ǰ�᣺cachedTRecord����ͬʱ������м�¼��������select�����в��ܼ�cache }
	private HashMap<String, HashMap<Object, Object>> cachedTRecord = new HashMap<String, HashMap<Object, Object>>();

	/**
	 * ����������
	 */
	public void add(Lockey lockey) {
		if (null == locks.get(lockey)) {
			// �ɹ��õ����Ժ�ż���������
			lockey.lock();
			locks.put(lockey, lockey);
		}
	}
	
	/**
	 * �����޸Ĺ��������{�Ż�}
	 */
	public void addLogNotifyTTable(TTable<?, ?> ttable) {
		logNotifyTTables.put(ttable.getName(), ttable);
	}
	
	/**
	 * ����TRecord�Ļ��棬{�Ż�}
	 */
	<K, V> void addCachedTRecord(TTable<K, V> ttable, TRecord<K, V> r) {
		HashMap<Object, Object> cachedForTable = cachedTRecord.get(ttable.getName());
		if (cachedForTable == null) {
			cachedForTable = new HashMap<Object, Object>();
			cachedTRecord.put(ttable.getName(), cachedForTable);
		}
		cachedForTable.put(r.getKey(), r);
	}
	
	/**
	 * ɾ��TRecord�Ļ��棬{�Ż�}
	 */
	<K, V> void rmvCachedTRecord(TTable<K, V> ttable, K k) {
		HashMap<Object, Object> cachedForTable = cachedTRecord.get(ttable.getName());
		if (cachedForTable != null) {
			cachedForTable.remove(k);
		}
	}
	
	/**
	 * ���TRecord�Ļ��棬{�Ż�}
	 */
	@SuppressWarnings("unchecked")
	<K, V> TRecord<K, V> getCachedTRecord(TTable<K, V> ttable, K k) {
		HashMap<Object, Object> cachedForTable = cachedTRecord.get(ttable.getName());
		if (cachedForTable == null) {
			cachedForTable = new HashMap<Object, Object>();
			cachedTRecord.put(ttable.getName(), cachedForTable);
		}
		return (TRecord<K, V>)cachedForTable.get(k);
	}
	
	/**
	 * ��������Ѽӵ�����{�Ż�}
	 */
	Lockey get(Lockey lockey) {
		return locks.get(lockey);
	}

	/**
	 * ���������ͷ�������������������wrapper��
	 */
	private void finish() {
		wrappers.clear();

		// û�а���lock��˳��unlock��
		for (Lockey lockey : locks.values()) {
			// Trace.debug("unlock " + lockey);
			try {
				lockey.unlock();
			} catch (Throwable e) {
				Trace.fatal("unlock " + lockey, e);
			}
		}
		locks.clear();
		cachedTRecord.clear();
	}

	//////////////////////////////////////////////////////////////////////
	public void begin() {
		++trancount;
	}

	public int trancount() {
		return trancount;
	}

	public void commit() {
		if (--trancount < 0)
			throw new XError("xdb: commit mismatch");
	}

	int savepoint() {
		logs.add(new Savepoint());
		return logs.size();
	}

	/**
	 * ���ص�ǰ������š�0 ��ʾû�б���㡣
	 */
	public int currentSavepointId() {
		return logs.size();
	}

//	/**
//	 * ���ص�ǰ����㡣null ��ʾû�б���㡣
//	 */
//	public Savepoint getCurrentSavepoint() {
//		if (logs.isEmpty())
//			return null;
//		return logs.get(logs.size() - 1);
//	}

	/**
	 * ���ݱ�ŵõ�����㡣null ��ʾû���ҵ�����㡣
	 */
	public Savepoint getSavepoint(int savepoint) {
		if (savepoint < 1 || savepoint > logs.size())
			return null;
		return logs.get(savepoint - 1);
	}

	public void rollback(int savepoint) {
		if (savepoint < 1 || savepoint > logs.size())
			throw new XError("xdb: invalid savepoint " + savepoint + "@" + logs.size());

		while (logs.size() >= savepoint)
			logs.remove(logs.size() - 1).rollback();

		--trancount;
		// �����Ҫ�ع����������׳��쳣��see Procedure.rollbackAll�������������ƥ��һ��begin��
	}

	private static ThreadLocal<Transaction> threadlocal = new ThreadLocal<Transaction>();

	public static final Transaction create() {
		Transaction t = threadlocal.get();
		if (null == t)
			threadlocal.set(t = new Transaction());
		return t;
	}

	public static final void destroy() {
		threadlocal.set(null);
	}

	public static final Transaction current() {
		return threadlocal.get();
	}

	public static final Savepoint currentSavepoint() {
		Transaction current = current();
		return current.logs.get(current.logs.size() - 1);
	}

	public static final void verify() {
		if (threadlocal.get() == null)
			throw new XError("Transaction.verify");
	}

	////////////////////////////////////////////////////////////////////////////
	// Procedure ִ�е㡣
	/**
	 * �ع����е��޸ģ����ػع�����־������
	 */
	private void _last_rollback_() {
//		int count = 0;
		try {
			for (int index = logs.size() - 1; index >= 0; --index)
				/*count += */logs.get(index).rollback();
			logs.clear();
			trancount = 0;
		} catch (Throwable err) {
			// ����������󣬴�ʱ�����Ѿ����ڲ�����״̬.
			Trace.fatal("last rollback ", err);
			Runtime.getRuntime().halt(54321);
		}
//		return count;
	}

	/**
	 * �ύ���е��޸ģ������ύ����־������
	 */
	private int _real_commit_() {
		if (0 != trancount) // ��� begin �� commit/rollback �Ƿ�ƥ��
			throw new XError("xdb: mismatch (begin,commit/rollback) trancount=" + trancount);
		int count = 0;
		for (Savepoint sp : logs)
			count += sp.commit();
		logs.clear();
		return count;
	}

	// �������ڵ������,û����������.
	private void logNotify(Procedure p) {
		try {
			int maxNestNotify = 255; // configure ?
			for (int nest = 0; nest < maxNestNotify; ++nest) {
				HashMap<String, TTable<?, ?>> curLogNotifyTTables = logNotifyTTables;
				logNotifyTTables = new HashMap<String, TTable<?, ?>>();
				for (TTable<?, ?> ttable : curLogNotifyTTables.values()) {
					ttable.logNotify();
				}
//				Xdb.getInstance().getTables().logNotify(); // δ�Ż�"ֻlogNotify�иı�ı���"ǰ�İ汾��
				if (_real_commit_() == 0)
					return; // Ƕ�׵�ͨ����������ˡ�
			}
			// ͨ�����̫�ࡣ���ܳ�����ѭ����
			Trace.fatal("reach maxNestNotify. proc=" + p.getClass().getName());
		} catch (Throwable e) {
			Trace.fatal("logNotify", e);
		}
		// ���Իع����һ��"Xdb.getInstance().getTables().logNotify()"�е��޸ġ�
		_last_rollback_();
		
		logNotifyTTables.clear(); // ����ʱ�����¼�����ı���
	}

	// ���洢�������ֱַ�ͳ������������
	private static xdb.util.Counter counter = new xdb.util.Counter(Xdb.mbeans(), "xdb", "Procedures");
	// �ܵ���������: totalCount = totalFalse + totalException + �ɹ�ִ�еġ�
	private static AtomicLong totalCount = new AtomicLong();
	private static AtomicLong totalFalse = new AtomicLong();
	private static AtomicLong totalException = new AtomicLong();

	public static long getTotalCount()       { return totalCount.get(); }
	public static long getTotalFalse()       { return totalFalse.get(); }
	public static long getTotalException()   { return totalException.get(); }

	public void perform(Procedure p) throws Throwable {
		try {
			// ���� = .True(δͳ�ƴ���) + .False + .Exception
			counter.increment(p.getClass().getName());
			totalCount.incrementAndGet();

			// flush lock . MEMORY���͵ı���������Ҫ�������Ϊ�˲����ӻ����̣��������⴦����
			Lock flushLock = Xdb.getInstance().getTables().flushReadLock();
			flushLock.lockInterruptibly();
			try {
				if (p.call()) {
					if (_real_commit_() > 0)
						logNotify(p);
					// else : û���޸ģ�����ҪlogNotify�����˹��̴����Ѿ�����ˡ�
					// .True
				} else {
					// ִ���߼�����falseͳ��
					counter.increment(p.getClass().getName() + ".False");
					totalFalse.incrementAndGet();
					_last_rollback_(); // Ӧ�÷��� false���ع���
				}

			} catch (Throwable e) {
				_last_rollback_(); // δ�������쳣���ع���
				throw e;

			} finally {
				this.doneRunAllTask();
				this.finish();
				flushLock.unlock();
			}

		} catch (Throwable e) {
			p.setException(e);
			p.setSuccess(false);
			// ִ���쳣ͳ��
			counter.increment(p.getClass().getName() + ".Exception");
			totalException.incrementAndGet();
			// ���е��쳣����Ӧ�ô������������׵�����������¼��־��
			Trace.log(p.getConf().getTrace(), "Transaction Perform Exception", e);
			throw e;
		}
	}

	
	
	////////////////////////////////
	/**
	 * ��������������ݼ�ֱ����Procedure.ppsendxxxx()�ķ��棬������ڣ�
	 * 1. ����ΪTransition.tpsendxxxx()~~~.....
	 * 2. ���������task�Ĳ���������***����call()�����󣬶�����û���ͷ�lock֮ǰ���õ�***
	 * 	  ��Procedure.ppsendxxxxx()�������������call()�����������ͷ�lock֮����õ�.
	 * 
	 * ʹ��ppsendǱ�ڵķ���:
	 * 	 ����
	 * 		ProcedureA{ lock(all); psend(PA); }
	 * 		ProcedureB{ lock(all); psend(PB); }
	 * 	��������£�
	 * 		ProcedureA��ִ�У�ProcedureB��ִ�У���ͻ������յ�PA�����յ�PB
	 * 	����һЩ���˵�����£�����psend������ʱ�������Ѿ��ſ�lock�ˣ���ʱ,�ͻ����п������յ�PB�����յ�PA
	 */
	//
	//add by cjc
	//
	
	public static void tsend(long roleid, xio.Protocol p) 
	{
		tpost(new SendToRole(roleid, p));
	}

	public static void tsend(long roleid1, long roleid2, xio.Protocol p) 
	{
		tpost(new SendToRoles(roleid1, roleid2, p));
	}

	public static void tsend(java.util.Collection<Long> roleids, xio.Protocol p) 
	{
		tpost(new SendToRoles(roleids, p));
	}

	public static void tsendResponse(xio.Protocol pFromLink, xio.Protocol p) 
	{
		tpost(new SendResponse(pFromLink, p));
	}

	public static void tbroadcast(xio.Protocol p, int timems) 
	{
		tpost(new Broadcast(p, timems));
	}

	public static void tsend(xio.Xio conn, xio.Protocol p) 
	{
		tpost(new SendToXio(conn, p));
	}

	public static void texecute(Procedure p) 
	{
		tpost(new ExecuteProcedure(p));
	}

	public static void texecute(Runnable r) 
	{
		tpost(new ExecuteRunnable(r));
	}
	//////////////////////////////////////////////////////////////////////////////
	// ���·����ύ�������ڴ洢���������ύʱ��Ч��

	public static void tsendWhileCommit(long roleid, xio.Protocol p) {
		tpostWhileCommit(new SendToRole(roleid, p));
	}

	public static void tsendWhileCommit(long roleid1, long roleid2, xio.Protocol p) {
		tpostWhileCommit(new SendToRoles(roleid1, roleid2, p));
	}

	public static void tsendWhileCommit(java.util.Collection<Long> roleids, xio.Protocol p) {
		tpostWhileCommit(new SendToRoles(roleids, p));
	}

	public static void tsendResponseWhileCommit(xio.Protocol pFromLink, xio.Protocol p) {
		tpostWhileCommit(new SendResponse(pFromLink, p));
	}

	public static void tbroadcastWhileCommit(xio.Protocol p, int timems) {
		tpostWhileCommit(new Broadcast(p, timems));
	}

	public static void tsendWhileCommit(xio.Xio conn, xio.Protocol p) {
		tpostWhileCommit(new SendToXio(conn, p));
	}

	public static void texecuteWhileCommit(Procedure p) {
		tpostWhileCommit(new ExecuteProcedure(p));
	}

	public static void texecuteWhileCommit(Runnable r) {
		tpostWhileCommit(new ExecuteRunnable(r));
	}
	///////////////////////////////////////////////////////////////////
	// ���·����ύ�������ڴ洢���̻ع�ʱ��Ч��
	public static void tsendWhileRollback(long roleid, xio.Protocol p) {
		tpostWhileRollback(new SendToRole(roleid, p));
	}

	public static void tsendWhileRollback(long roleid1, long roleid2, xio.Protocol p) {
		tpostWhileRollback(new SendToRoles(roleid1, roleid2, p));
	}

	public static void tsendWhileRollback(java.util.Collection<Long> roleids, xio.Protocol p) {
		tpostWhileRollback(new SendToRoles(roleids, p));
	}

	public static void tsendResponseWhileRollback(xio.Protocol pFromLink, xio.Protocol p) {
		tpostWhileRollback(new SendResponse(pFromLink, p));
	}

	public static void tbroadcastWhileRollback(xio.Protocol p, int timems) {
		tpostWhileRollback(new Broadcast(p, timems));
	}

	public static void tsendWhileRollback(xio.Xio conn, xio.Protocol p) {
		tpostWhileRollback(new SendToXio(conn, p));
	}

	public static void texecuteWhileRollback(Procedure p) {
		tpostWhileRollback(new ExecuteProcedure(p));
	}

	public static void texecuteWhileRollback(Runnable r) {
		tpostWhileRollback(new ExecuteRunnable(r));
	}
	
	private static void tpostWhileCommit(Task task) 
	{
		task.setExpected(true);
		Transaction.currentSavepoint().add(new LogKey(new XBean(null, null), ""), task);
		tpost(task);
	}

	private static void tpostWhileRollback(Task task) 
	{
		task.setExpected(false);
		Transaction.currentSavepoint().add(new LogKey(new XBean(null, null), ""), task);
		tpost(task);
	}
	
	private static void tpost(Task task) 
	{
		current()._ttasks.add(task);
	}
	
	private ArrayList<Task> _ttasks = 
		new ArrayList<Task>();
	
	private void doneRunAllTask()
	{
		for (Task task : this._ttasks) 
		{
			try 
			{
				task.process();
			} catch (Throwable e) 
			{//������¼��־
				xdb.Trace.error("Transaction.runTasks", e);
			}
		}
		_ttasks.clear();
	}
	
}