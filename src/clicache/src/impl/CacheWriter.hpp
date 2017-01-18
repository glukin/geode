/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//#include "../gf_includes.hpp"
//#include "../../../ICacheWriter.hpp"
#include "../CacheWriterAdapter.hpp"
#include "../ICacheWriter.hpp"
//#include "../Region.hpp"
//#include "../../../Region.hpp"
//#include "../../../Cache.hpp"

using namespace System;

using namespace GemStone::GemFire::Cache;

namespace GemStone
{
  namespace GemFire
  {
    namespace Cache { namespace Generic
    {

      generic<class TKey, class TValue>
      public ref class CacheWriterGeneric : GemStone::GemFire::Cache::Generic::CacheWriterAdapter<Object^, Object^>
      {
        private:

          ICacheWriter<TKey, TValue>^ m_writer;

        public:

          void SetCacheWriter(ICacheWriter<TKey, TValue>^ writer)
          {
            m_writer = writer;
          }

          virtual bool BeforeUpdate( GemStone::GemFire::Cache::Generic::EntryEvent<Object^, Object^>^ ev ) override
          {
            EntryEvent<TKey, TValue> gevent(Generic::GetNativePtr<gemfire::EntryEvent>(ev));
            return m_writer->BeforeUpdate(%gevent);
          }

          virtual bool BeforeCreate(GemStone::GemFire::Cache::Generic::EntryEvent<Object^, Object^>^ ev) override
          {
            EntryEvent<TKey, TValue> gevent(Generic::GetNativePtr<gemfire::EntryEvent>(ev));
            return m_writer->BeforeCreate(%gevent);
          }

          virtual bool BeforeDestroy(GemStone::GemFire::Cache::Generic::EntryEvent<Object^, Object^>^ ev) override
          {
            EntryEvent<TKey, TValue> gevent(Generic::GetNativePtr<gemfire::EntryEvent>(ev));
            return m_writer->BeforeDestroy(%gevent);
          }

          virtual bool BeforeRegionClear( GemStone::GemFire::Cache::Generic::RegionEvent<Object^, Object^>^ ev ) override
          {
            RegionEvent<TKey, TValue> gevent(Generic::GetNativePtr<gemfire::RegionEvent>(ev));
            return m_writer->BeforeRegionClear(%gevent);
          }

          virtual bool BeforeRegionDestroy(GemStone::GemFire::Cache::Generic::RegionEvent<Object^, Object^>^ ev) override
          {
            RegionEvent<TKey, TValue> gevent(Generic::GetNativePtr<gemfire::RegionEvent>(ev));
            return m_writer->BeforeRegionDestroy(%gevent);
          }
          
          virtual void Close(GemStone::GemFire::Cache::Generic::Region<Object^, Object^>^ region) override
          {
            IRegion<TKey, TValue>^ gregion = GemStone::GemFire::Cache::Generic::Region<TKey, TValue>::Create(Generic::GetNativePtr<gemfire::Region>(region));
            m_writer->Close(gregion);
          }
      };
    }
    }
  }
}