// cc  -I [path to CL hdr files] opencltest.c -L /vendor/lib64 -l OpenCL -o libchkopenCL.so
     
#define CL_TARGET_OPENCL_VERSION 300
#include <CL/cl.h>

typedef enum {
    BACKEND_CPU,
    BACKEND_OPENCL
} backend_t;

static backend_t choose_backend(void)
{
    cl_uint platform_count = 0;
    cl_int err = clGetPlatformIDs(0, NULL, &platform_count);
    if (err != CL_SUCCESS || platform_count == 0) {
        return BACKEND_CPU;
    }
    cl_platform_id platforms[16];
    cl_uint count = platform_count < 16 ? platform_count : 16;
    err = clGetPlatformIDs(count, platforms, NULL);
    if (err != CL_SUCCESS) {
        return BACKEND_CPU;
    }
    for (cl_uint i = 0; i < count; ++i) {
        cl_uint device_count = 0;
        err = clGetDeviceIDs(platforms[i], CL_DEVICE_TYPE_GPU,
                             0, NULL, &device_count);
        if (err == CL_SUCCESS && device_count > 0) {
            return BACKEND_OPENCL;
        }
    }
    return BACKEND_CPU;
}

int main(void)
{
    backend_t backend = choose_backend();
    if (backend == BACKEND_OPENCL) {
        return 0;
    }
    return -1;
}
