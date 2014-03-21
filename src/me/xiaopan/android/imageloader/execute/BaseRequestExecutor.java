/*
 * Copyright (C) 2013 Peng fei Pan <sky@xiaopan.me>
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package me.xiaopan.android.imageloader.execute;

import java.io.File;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import me.xiaopan.android.imageloader.ImageLoader;
import me.xiaopan.android.imageloader.task.load.*;
import me.xiaopan.android.imageloader.task.TaskRequest;
import me.xiaopan.android.imageloader.task.display.*;
import me.xiaopan.android.imageloader.task.download.DownloadRequest;
import me.xiaopan.android.imageloader.task.download.DownloadTask;
import me.xiaopan.android.imageloader.util.Scheme;
import android.util.Log;

/**
 * 基本的任务执行器
 */
public class BaseRequestExecutor implements RequestExecutor {
	private static final String NAME= BaseRequestExecutor.class.getSimpleName();
	private Executor taskDispatchExecutor;	//任务调度执行器
	private Executor netTaskExecutor;	//网络任务执行器
	private Executor localTaskExecutor;	//本地任务执行器
	
	public BaseRequestExecutor(Executor taskDispatchExecutor, Executor netTaskExecutor, Executor localTaskExecutor){
		this.taskDispatchExecutor = taskDispatchExecutor;
		this.netTaskExecutor = netTaskExecutor;
		this.localTaskExecutor = localTaskExecutor;
	}
	
	public BaseRequestExecutor(Executor netTaskExecutor){
		this(
			new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(20), new ThreadPoolExecutor.DiscardOldestPolicy()),
			netTaskExecutor, 
			new ThreadPoolExecutor(1, 1, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(20), new ThreadPoolExecutor.DiscardOldestPolicy())
		);
	}
	
	public BaseRequestExecutor(){
		this(new ThreadPoolExecutor(5, 10, 1, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>(20), new ThreadPoolExecutor.DiscardOldestPolicy()));
	}
	
	@Override
	public void execute(final TaskRequest taskRequest) {
		taskDispatchExecutor.execute(new Runnable() {
			@Override
			public void run() {
				if(taskRequest instanceof DisplayRequest){
                    executeDisplayRequest((DisplayRequest) taskRequest);
                }else if(taskRequest instanceof LoadRequest){
					executeLoadRequest((LoadRequest) taskRequest);
				}else if(taskRequest instanceof DownloadRequest){
                    executeDownloadRequest((DownloadRequest) taskRequest);
                }
            }
		});
	}
	
	/**
	 * 执行下载请求
	 * @param downloadRequest 下载请求
	 */
	private void executeDownloadRequest(DownloadRequest downloadRequest){
		File cacheFile = downloadRequest.getConfiguration().getBitmapCacher().getCacheFile(downloadRequest);
		downloadRequest.setCacheFile(cacheFile);
		if(cacheFile != null && cacheFile.exists()){
			localTaskExecutor.execute(new DownloadTask(downloadRequest));
			if(downloadRequest.getConfiguration().isDebugMode()){
				Log.d(ImageLoader.LOG_TAG, new StringBuffer(NAME).append("：").append("DOWNLOAD - 本地").append("；").append(downloadRequest.getName()).toString());
			}
		}else{
			netTaskExecutor.execute(new DownloadTask(downloadRequest));
			if(downloadRequest.getConfiguration().isDebugMode()){
				Log.d(ImageLoader.LOG_TAG, new StringBuffer(NAME).append("：").append("DOWNLOAD - 网络").append("；").append(downloadRequest.getName()).toString());
			}
		}
	}
	
	/**
	 * 执行加载请求
	 * @param loadRequest 记载请求
	 */
	private void executeLoadRequest(LoadRequest loadRequest){
		Scheme scheme = Scheme.ofUri(loadRequest.getUri());
		switch(scheme){
			case HTTP :
			case HTTPS : 
				File cacheFile = loadRequest.getConfiguration().getBitmapCacher().getCacheFile(loadRequest);
                loadRequest.setCacheFile(cacheFile);
                if(cacheFile != null && cacheFile.exists()){
                    localTaskExecutor.execute(new BitmapLoadTask(loadRequest, new BitmapLoadCallable(loadRequest, new CacheFileOnDecodeListener(cacheFile, loadRequest))));
                    if(loadRequest.getConfiguration().isDebugMode()){
                        Log.d(ImageLoader.LOG_TAG, new StringBuffer(NAME).append("：").append("LOAD - HTTP - 本地").append("；").append(loadRequest.getName()).toString());
                    }
                }else{
                    netTaskExecutor.execute(new DownloadTask(loadRequest.setDownloadListener(new LoadJoinDownloadListener(localTaskExecutor, loadRequest))));
                    if(loadRequest.getConfiguration().isDebugMode()){
                        Log.d(ImageLoader.LOG_TAG, new StringBuffer(NAME).append("：").append("LOAD - HTTP - 网络").append("；").append(loadRequest.getName()).toString());
                    }
                }
				break;
			case FILE :
                localTaskExecutor.execute(new BitmapLoadTask(loadRequest, new BitmapLoadCallable(loadRequest, new FileOnDecodeListener(new File(Scheme.FILE.crop(loadRequest.getUri())), loadRequest))));
                if(loadRequest.getConfiguration().isDebugMode()){
                    Log.d(ImageLoader.LOG_TAG, new StringBuffer(NAME).append("：").append("LOAD - FILE").append("；").append(loadRequest.getName()).toString());
                }
				break;
			case ASSETS :
                localTaskExecutor.execute(new BitmapLoadTask(loadRequest, new BitmapLoadCallable(loadRequest, new AssetsOnDecodeListener(Scheme.ASSETS.crop(loadRequest.getUri()), loadRequest))));
                if(loadRequest.getConfiguration().isDebugMode()){
                    Log.d(ImageLoader.LOG_TAG, new StringBuffer(NAME).append("：").append("LOAD - ASSETS").append("；").append(loadRequest.getName()).toString());
                }
				break;
			case CONTENT :
                localTaskExecutor.execute(new BitmapLoadTask(loadRequest, new BitmapLoadCallable(loadRequest, new ContentOnDecodeListener(loadRequest.getUri(), loadRequest))));
                if(loadRequest.getConfiguration().isDebugMode()){
                    Log.d(ImageLoader.LOG_TAG, new StringBuffer(NAME).append("：").append("LOAD - CONTENT").append("；").append(loadRequest.getName()).toString());
                }
				break;
			case DRAWABLE :
                localTaskExecutor.execute(new BitmapLoadTask(loadRequest, new BitmapLoadCallable(loadRequest, new DrawableOnDecodeListener(Scheme.DRAWABLE.crop(loadRequest.getUri()), loadRequest))));
                if(loadRequest.getConfiguration().isDebugMode()){
                    Log.d(ImageLoader.LOG_TAG, new StringBuffer(NAME).append("：").append("LOAD - DRAWABLE").append("；").append(loadRequest.getName()).toString());
                }
                break;
			default:
                if(loadRequest.getConfiguration().isDebugMode()){
                    Log.e(ImageLoader.LOG_TAG, new StringBuffer(NAME).append("：").append("LOAD - 未知的协议格式").append("：").append(loadRequest.getUri()).toString());
                }
				break;
		}
	}

    /**
     * 执行显示请求
     * @param displayRequest 显示请求
     */
    private void executeDisplayRequest(DisplayRequest displayRequest){
        executeLoadRequest(displayRequest.setLoadListener(new DisplayJoinLoadListener(displayRequest)));
    }
}
