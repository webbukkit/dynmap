public class CacheTest
{
	Cache<String, Integer> cache;

	void add(String key, Integer val)
	{
		Integer old = cache.put(key, val);
		if(old == null) {
			System.out.println("cache.put(" + key + ", " + val + ") == null");
		} else {
			System.out.println("cache.put(" + key + ", " + val + ") == " + old);
		}
	}

	void get(String key)
	{
		Integer v = cache.get(key);
		if(v == null) {
			System.out.println("cache.get(" + key + ") == null");
		} else {
			System.out.println("cache.get(" + key + ") == " + v);
		}
	}

	public void test()
	{
		cache = new Cache<String, Integer>(5);

		add("a", 1);
		add("b", 2);
		add("c", 3);
		add("d", 4);
		add("e", 5);
		add("f", 6);
		add("g", 7);
		add("h", 8);
		add("i", 9);
		add("j", 10);

		get("a");
		get("b");
		get("c");
		get("d");
		get("e");
		get("f");
		get("g");
		get("h");
		get("i");
		get("j");

		add("g", 11);
		add("i", 12);

		get("f");
		get("g");
		get("h");
		get("i");
		get("j");

		add("k", 13);
		add("l", 14);
		add("m", 15);
		add("n", 16);
		add("o", 17);
	}

	public static void main(String[] args)
	{
		CacheTest k = new CacheTest();
		k.test();
	}
}
