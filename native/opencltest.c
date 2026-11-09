// cc  -I [path to CL hdr files] opencltest.c -L /vendor/lib64 -l OpenCL -o libchkopenCL.so
     
#define CL_TARGET_OPENCL_VERSION 300
#include <CL/cl.h>
#include <string.h>

int chk_backend(void)
{
    cl_uint platform_count = 0;
    cl_int err = clGetPlatformIDs(0, NULL, &platform_count);
    if (err != CL_SUCCESS || platform_count == 0) {
        return -1;
    }
    cl_platform_id platforms[16];
    cl_uint count = platform_count < 16 ? platform_count : 16;
    err = clGetPlatformIDs(count, platforms, NULL);
    if (err != CL_SUCCESS) {
        return -1;
    }
    for (cl_uint i = 0; i < count; ++i) {
        cl_uint device_count = 0;
        cl_device_id devices[16];
        err = clGetDeviceIDs(platforms[i], CL_DEVICE_TYPE_GPU,
                             16,devices, &device_count);
        if (err == CL_SUCCESS && device_count > 0) {
            cl_uint count2 = device_count < 16 ? device_count : 16;
            char buffer[99];
            for (cl_uint j = 0; j < count2; ++j) {
                err = clGetDeviceInfo(devices[j], CL_DEVICE_NAME, 99, buffer, NULL);
                if (err == CL_SUCCESS && strstr(buffer, "Adreno")) {
                    return 0;
                }
            }
        }
    }
    return -1;
}

int main(void)
{
    return chk_backend();
}
