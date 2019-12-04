package com.criteo.publisher.mock;

import static org.mockito.internal.exceptions.Reporter.noArgumentValueWasCaptured;

import java.util.LinkedList;
import java.util.List;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class ResultCaptor<T> implements Answer<T> {

  private final LinkedList<T> results = new LinkedList<>();

  @Override
  public T answer(InvocationOnMock invocation) throws Throwable {
    T result = (T) invocation.callRealMethod();
    results.add(result);
    return result;
  }

  /**
   * Returns the captured value of the result.
   * <p>
   * If verified method was called multiple times then this method it returns the latest captured value.
   * <p>
   * Example:
   * <pre class="code"><code class="java">
   *   ResultCaptor&lt;Person&gt; peopleCaptor = new ResultCaptor&lt;&gt;();
   *   doReturn(peopleCaptor).when(mock).createPeople();
   *
   *   mock.createPeople(); // Return John first
   *   mock.createPeople(); // Return Jame then
   *
   *   Person capturedPeople = peopleCaptor.getValue();
   *   assertEquals("Jane", capturedPeople.getName());
   * </pre>
   *
   * @return captured result value
   */
  public T getLastCaptureValue() {
    if (results.isEmpty()) {
      throw noArgumentValueWasCaptured();
    }
    return (T) results.getLast();
  }

  /**
   * Returns all captured values. Use it when the verified method was called multiple times.
   * <p>
   * Example:
   * <pre class="code"><code class="java">
   *   ResultCaptor&lt;Person&gt; peopleCaptor = new ResultCaptor&lt;&gt;();
   *   doReturn(peopleCaptor).when(mock).createPeople();
   *
   *   mock.createPeople(); // Return John first
   *   mock.createPeople(); // Return Jame then
   *
   *   List&lt;Person&gt; capturedPeople = peopleCaptor.getAllValues();
   *   assertEquals("John", capturedPeople.get(0).getName());
   *   assertEquals("Jane", capturedPeople.get(1).getName());
   * </pre>
   *
   * @return captured argument value
   */
  public List<T> getAllValues() {
    return results;
  }

}
